package io.casehub.work.runtime.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.util.*;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import io.casehub.platform.api.routing.StrategyResolver;
import io.casehub.work.api.AssignmentDecision;
import io.casehub.work.api.Capability;
import io.casehub.work.api.PolicyDecision;
import io.casehub.work.api.AssignmentTrigger;
import io.casehub.work.api.SelectionContext;
import io.casehub.work.api.WorkerCandidate;
import io.casehub.work.api.spi.WorkerRegistry;
import io.casehub.work.api.spi.WorkerSelectionStrategy;
import io.casehub.work.api.spi.WorkloadProvider;
import io.casehub.work.core.strategy.LeastLoadedStrategy;

import io.casehub.work.api.WorkItem;

/**
 * Unit tests for WorkItemAssignmentService — no Quarkus boot.
 * Issue #116, Epics #100/#102.
 */
@ExtendWith(MockitoExtension.class)
class WorkItemAssignmentServiceTest {

    @Mock
    WorkloadProvider workloadProvider;
    @Mock
    WorkerRegistry workerRegistry;

    private WorkItemAssignmentService service;

    @BeforeEach
    void setUp() {
        lenient().when(workerRegistry.resolveGroup(anyString())).thenReturn(List.of());
        lenient().when(workloadProvider.getActiveWorkCount(anyString())).thenReturn(0);
        service = serviceWith(new LeastLoadedStrategy());
    }

    private WorkItemAssignmentService serviceWith(final WorkerSelectionStrategy strategy) {
        final StrategyResolver resolver = mock(StrategyResolver.class);
        lenient().when(resolver.resolve(eq(WorkerSelectionStrategy.class), anyString())).thenReturn(strategy);
        return new WorkItemAssignmentService(resolver, "least-loaded",
                workerRegistry, workloadProvider,
                (userId, excluded) -> PolicyDecision.ALLOW);
    }

    // ── Trigger filtering ─────────────────────────────────────────────────────

    @Test
    void assign_skipsWork_whenTriggerNotInStrategyTriggers() {
        final WorkerSelectionStrategy createdOnly = new WorkerSelectionStrategy() {
            @Override
            public String id() { return "test-created-only"; }

            @Override
            public AssignmentDecision select(SelectionContext c, List<WorkerCandidate> w) {
                return AssignmentDecision.assignTo("alice");
            }

            @Override
            public Set<AssignmentTrigger> triggers() {
                return Set.of(AssignmentTrigger.CREATED);
            }
        };
        service = serviceWith(createdOnly);

        final WorkItem wi = workItem(null, null, "alice,bob");
        final WorkItem result = service.assign(wi, AssignmentTrigger.RELEASED);
        assertThat(result.assigneeId()).isNull();
    }

    @Test
    void assign_fires_whenTriggerIsInStrategyTriggers() {
        when(workloadProvider.getActiveWorkCount("alice")).thenReturn(0);
        final WorkItem wi = workItem(null, null, "alice");
        final WorkItem result = service.assign(wi, AssignmentTrigger.CREATED);
        assertThat(result.assigneeId()).isEqualTo("alice");
    }

    // ── candidateUsers resolution ─────────────────────────────────────────────

    @Test
    void assign_parsesCandidateUsers_asCommaDelimitedList() {
        when(workloadProvider.getActiveWorkCount("alice")).thenReturn(5);
        when(workloadProvider.getActiveWorkCount("bob")).thenReturn(1);
        when(workloadProvider.getActiveWorkCount("carol")).thenReturn(3);
        final WorkItem wi = workItem(null, null, "alice,bob,carol");
        final WorkItem result = service.assign(wi, AssignmentTrigger.CREATED);
        assertThat(result.assigneeId()).isEqualTo("bob");
    }

    @Test
    void assign_trimsWhitespace_inCandidateUsers() {
        when(workloadProvider.getActiveWorkCount("alice")).thenReturn(0);
        when(workloadProvider.getActiveWorkCount("bob")).thenReturn(2);
        final WorkItem wi = workItem(null, null, " alice , bob ");
        final WorkItem result = service.assign(wi, AssignmentTrigger.CREATED);
        assertThat(result.assigneeId()).isEqualTo("alice");
    }

    @Test
    void assign_noChange_whenNoCandidateUsersAndNoGroupResolution() {
        final WorkItem wi = workItem(null, null, null);
        final WorkItem result = service.assign(wi, AssignmentTrigger.CREATED);
        assertThat(result.assigneeId()).isNull();
    }

    // ── candidateGroups resolution via WorkerRegistry ─────────────────────────

    @Test
    void assign_resolvesGroup_viaWorkerRegistry() {
        when(workerRegistry.resolveGroup("finance-team")).thenReturn(
                List.of(WorkerCandidate.of("alice"), WorkerCandidate.of("bob")));
        when(workloadProvider.getActiveWorkCount("alice")).thenReturn(3);
        when(workloadProvider.getActiveWorkCount("bob")).thenReturn(0);
        final WorkItem wi = workItem(null, "finance-team", null);
        final WorkItem result = service.assign(wi, AssignmentTrigger.CREATED);
        assertThat(result.assigneeId()).isEqualTo("bob");
    }

    @Test
    void assign_deduplicates_candidatesFromUsersAndGroups() {
        when(workerRegistry.resolveGroup("team")).thenReturn(
                List.of(WorkerCandidate.of("alice")));
        when(workloadProvider.getActiveWorkCount("alice")).thenReturn(2);
        when(workloadProvider.getActiveWorkCount("bob")).thenReturn(1);
        final WorkItem wi = workItem(null, "team", "alice,bob");
        final WorkItem result = service.assign(wi, AssignmentTrigger.CREATED);
        assertThat(result.assigneeId()).isEqualTo("bob");
    }

    // ── requiredCapabilities filtering ────────────────────────────────────────

    @Test
    void assign_filtersOut_candidatesFromRegistry_withoutRequiredCapabilities() {
        when(workerRegistry.resolveGroup("team")).thenReturn(List.of(
                new WorkerCandidate("alice", Set.of(Capability.of("audit"), Capability.of("legal")), 0),
                new WorkerCandidate("bob", Set.of(Capability.of("sales")), 0)));
        final WorkItem wi = workItem(null, "team", null)
                .toBuilder().requiredCapabilities("audit").build();
        final WorkItem result = service.assign(wi, AssignmentTrigger.CREATED);
        assertThat(result.assigneeId()).isEqualTo("alice");
    }

    @Test
    void assign_returnsNoChange_whenAllCandidatesLackRequiredCapabilities() {
        when(workerRegistry.resolveGroup("team")).thenReturn(List.of(
                new WorkerCandidate("alice", Set.of(Capability.of("sales")), 0)));
        final WorkItem wi = workItem(null, "team", null)
                .toBuilder().requiredCapabilities("audit").build();
        final WorkItem result = service.assign(wi, AssignmentTrigger.CREATED);
        assertThat(result.assigneeId()).isNull();
    }

    @Test
    void assign_candidateUsersHaveNoCapabilities_soAllFilteredWhenCapRequired() {
        // candidateUsers get WorkerCandidate.of() — empty capabilities
        final WorkItem wi = workItem(null, null, "alice,bob")
                .toBuilder().requiredCapabilities("exotic-skill").build();
        final WorkItem result = service.assign(wi, AssignmentTrigger.CREATED);
        assertThat(result.assigneeId()).isNull();
    }

    // ── AssignmentDecision application ────────────────────────────────────────

    @Test
    void assign_setsAssigneeId_fromDecision() {
        when(workloadProvider.getActiveWorkCount("alice")).thenReturn(0);
        final WorkItem wi = workItem(null, null, "alice");
        final WorkItem result = service.assign(wi, AssignmentTrigger.CREATED);
        assertThat(result.assigneeId()).isEqualTo("alice");
    }

    @Test
    void assign_setsCandidateGroups_fromNarrowDecision() {
        final WorkerSelectionStrategy narrower = new WorkerSelectionStrategy() {
            @Override public String id() { return "test-narrower"; }
            @Override public AssignmentDecision select(final SelectionContext ctx, final List<WorkerCandidate> c) {
                return AssignmentDecision.narrowCandidates("narrowed-group", null);
            }
        };
        service = serviceWith(narrower);
        final WorkItem wi = workItem(null, "original-group", null);
        final WorkItem result = service.assign(wi, AssignmentTrigger.CREATED);
        assertThat(result.candidateGroups()).isEqualTo("narrowed-group");
        assertThat(result.assigneeId()).isNull();
    }

    @Test
    void assign_doesNotOverwrite_existingFields_onNoChange() {
        final WorkerSelectionStrategy noOp = new WorkerSelectionStrategy() {
            @Override public String id() { return "test-noop"; }
            @Override public AssignmentDecision select(final SelectionContext ctx, final List<WorkerCandidate> c) {
                return AssignmentDecision.noChange();
            }
        };
        service = serviceWith(noOp);
        final WorkItem wi = workItem(null, null, "alice")
                .toBuilder().assigneeId("pre-existing").build();
        final WorkItem result = service.assign(wi, AssignmentTrigger.CREATED);
        assertThat(result.assigneeId()).isEqualTo("pre-existing");
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private WorkItem workItem(final String groups, final String groupsOnly, final String users) {
        return WorkItem.builder()
                .id(UUID.randomUUID())
                .candidateGroups(groups != null ? groups : groupsOnly)
                .candidateUsers(users)
                .build();
    }
}
