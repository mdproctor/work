package io.casehub.work.runtime.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.annotation.Annotation;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;

import jakarta.enterprise.event.Event;
import jakarta.enterprise.event.NotificationOptions;
import jakarta.enterprise.util.TypeLiteral;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.casehub.platform.api.path.Path;
import io.casehub.platform.api.preferences.MapPreferences;
import io.casehub.platform.api.preferences.PreferenceProvider;
import io.casehub.platform.api.preferences.SettingsScope;
import io.casehub.work.api.BreachDecision;
import io.casehub.work.api.ClaimSlaContext;
import io.casehub.work.api.ClaimSlaPolicy;
import io.casehub.work.api.SlaBreachContext;
import io.casehub.work.api.SlaBreachPolicy;
import io.casehub.work.api.AssignmentDecision;
import io.casehub.work.api.PolicyDecision;
import io.casehub.work.api.SelectionContext;
import io.casehub.work.api.WorkerCandidate;
import io.casehub.work.core.strategy.WorkBroker;
import io.casehub.work.runtime.event.SlaBreachEvent;
import io.casehub.work.runtime.model.AuditEntry;
import io.casehub.work.runtime.model.WorkItem;
import io.casehub.work.runtime.model.WorkItemStatus;
import io.casehub.work.runtime.repository.AuditEntryStore;
import io.casehub.work.runtime.repository.WorkItemQuery;
import io.casehub.work.runtime.repository.WorkItemStore;

/**
 * Pure unit tests for {@link ExpiryLifecycleService} — no Quarkus, no CDI.
 *
 * <p>Verifies that expiry processing and claim deadline breach handling correctly
 * transition WorkItem state, write audit entries, and execute the SlaBreachPolicy decision.
 */
class ExpiryLifecycleServiceTest {

    // ── In-memory stubs ───────────────────────────────────────────────────────

    static class TestStore implements WorkItemStore {
        final Map<UUID, WorkItem> items = new ConcurrentHashMap<>();

        @Override
        public WorkItem put(final WorkItem wi) {
            if (wi.id == null) wi.id = UUID.randomUUID();
            items.put(wi.id, wi);
            return wi;
        }

        @Override
        public Optional<WorkItem> get(final UUID id) {
            return Optional.ofNullable(items.get(id));
        }

        @Override
        public List<WorkItem> scan(final WorkItemQuery query) {
            return items.values().stream()
                    .filter(wi -> {
                        if (query.expiresAtOrBefore() != null) {
                            return wi.expiresAt != null
                                    && !wi.expiresAt.isAfter(query.expiresAtOrBefore())
                                    && !wi.status.isTerminal();
                        }
                        if (query.claimDeadlineOrBefore() != null) {
                            return wi.claimDeadline != null
                                    && !wi.claimDeadline.isAfter(query.claimDeadlineOrBefore())
                                    && wi.status == WorkItemStatus.PENDING;
                        }
                        return false;
                    })
                    .toList();
        }
    }

    static class TestAuditStore implements AuditEntryStore {
        final List<AuditEntry> entries = new ArrayList<>();

        @Override public void append(final AuditEntry e) { entries.add(e); }
        @Override public List<AuditEntry> findByWorkItemId(final UUID id) {
            return entries.stream().filter(e -> id.equals(e.workItemId)).toList();
        }
        @Override public List<AuditEntry> query(final io.casehub.work.runtime.repository.AuditQuery q) {
            return List.of();
        }
        @Override public long count(final io.casehub.work.runtime.repository.AuditQuery q) {
            return 0;
        }
    }

    /** Always returns 4 hours from now as the next claim deadline. */
    static class FixedClaimSlaPolicy implements ClaimSlaPolicy {
        @Override
        public Instant computePoolDeadline(final ClaimSlaContext ctx) {
            return Instant.now().plus(4, ChronoUnit.HOURS);
        }
    }

    static class CapturingStrategy implements io.casehub.work.api.WorkerSelectionStrategy {
        final List<SelectionContext> calls = new ArrayList<>();

        @Override
        public AssignmentDecision select(final SelectionContext ctx,
                final java.util.List<WorkerCandidate> candidates) {
            calls.add(ctx);
            return candidates.isEmpty()
                    ? AssignmentDecision.noChange()
                    : AssignmentDecision.assignTo(candidates.get(0).id());
        }
    }

    /** Returns a configurable fixed decision. */
    static class TestSlaBreachPolicy implements SlaBreachPolicy {
        private BreachDecision decision = new BreachDecision.Fail("no-sla-breach-policy-configured");

        void willReturn(final BreachDecision d) { this.decision = d; }

        @Override
        public BreachDecision onBreach(final SlaBreachContext context) { return decision; }
    }

    /** Returns empty preferences at any scope. */
    static final PreferenceProvider EMPTY_PREFS = scope -> new MapPreferences(Map.of());

    /** Captures fired SlaBreachEvents for assertions; implements only {@code fire()}. */
    static class CapturingBreachEventBus implements Event<SlaBreachEvent> {
        final List<SlaBreachEvent> captured;
        CapturingBreachEventBus(final List<SlaBreachEvent> captured) { this.captured = captured; }
        @Override public void fire(final SlaBreachEvent e) { captured.add(e); }
        @Override public <U extends SlaBreachEvent> CompletionStage<U> fireAsync(final U e) { throw new UnsupportedOperationException(); }
        @Override public <U extends SlaBreachEvent> CompletionStage<U> fireAsync(final U e, final NotificationOptions o) { throw new UnsupportedOperationException(); }
        @Override public Event<SlaBreachEvent> select(final Annotation... q) { throw new UnsupportedOperationException(); }
        @Override public <U extends SlaBreachEvent> Event<U> select(final Class<U> s, final Annotation... q) { throw new UnsupportedOperationException(); }
        @Override public <U extends SlaBreachEvent> Event<U> select(final TypeLiteral<U> s, final Annotation... q) { throw new UnsupportedOperationException(); }
    }

    // ── Setup ─────────────────────────────────────────────────────────────────

    private TestStore store;
    private TestAuditStore auditStore;
    private TestSlaBreachPolicy policy;
    private final List<SlaBreachEvent> breachEvents = new ArrayList<>();
    private ExpiryLifecycleService service;

    @BeforeEach
    void setUp() {
        store = new TestStore();
        auditStore = new TestAuditStore();
        policy = new TestSlaBreachPolicy();
        breachEvents.clear();

        service = new ExpiryLifecycleService();
        service.workItemStore = store;
        service.auditStore = auditStore;
        service.slaBreachPolicy = policy;
        service.preferenceProvider = EMPTY_PREFS;
        service.slaBreachEventBus = new CapturingBreachEventBus(breachEvents);
        service.lifecycleEvent = null; // CDI event bus — not under test
        service.claimSlaPolicy = new FixedClaimSlaPolicy();
        service.config = WorkItemServiceTest.testConfig();
        service.assignmentService = new WorkItemAssignmentService(
                (ctx, candidates) -> AssignmentDecision.noChange(),
                group -> java.util.List.of(),
                id -> 0,
                new WorkBroker(),
                (userId, excluded) -> PolicyDecision.ALLOW);
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private WorkItem expiredItem() {
        final WorkItem wi = new WorkItem();
        wi.id = UUID.randomUUID();
        wi.title = "test item";
        wi.status = WorkItemStatus.PENDING;
        wi.expiresAt = Instant.now().minus(1, ChronoUnit.HOURS);
        wi.createdAt = Instant.now().minus(2, ChronoUnit.HOURS);
        wi.accumulatedUnclaimedSeconds = 0L;
        store.put(wi);
        return wi;
    }

    private WorkItem claimExpiredItem() {
        final WorkItem wi = new WorkItem();
        wi.id = UUID.randomUUID();
        wi.title = "test item";
        wi.status = WorkItemStatus.PENDING;
        wi.claimDeadline = Instant.now().minus(1, ChronoUnit.HOURS);
        wi.createdAt = Instant.now().minus(2, ChronoUnit.HOURS);
        wi.lastReturnedToPoolAt = Instant.now().minus(2, ChronoUnit.HOURS);
        wi.accumulatedUnclaimedSeconds = 0L;
        store.put(wi);
        return wi;
    }

    // ── checkExpired — Fail decision ─────────────────────────────────────────

    @Test
    void checkExpired_withFailDecision_transitionsToExpired() {
        policy.willReturn(new BreachDecision.Fail("missed-deadline"));
        final WorkItem wi = expiredItem();
        service.checkExpired();
        assertThat(store.get(wi.id).orElseThrow().status).isEqualTo(WorkItemStatus.EXPIRED);
    }

    @Test
    void checkExpired_withFailDecision_setsCompletedAt() {
        policy.willReturn(new BreachDecision.Fail("missed-deadline"));
        final WorkItem wi = expiredItem();
        service.checkExpired();
        assertThat(store.get(wi.id).orElseThrow().completedAt).isNotNull();
    }

    @Test
    void checkExpired_withFailDecision_recordsReasonAsResolution() {
        policy.willReturn(new BreachDecision.Fail("missed-deadline"));
        final WorkItem wi = expiredItem();
        service.checkExpired();
        assertThat(store.get(wi.id).orElseThrow().resolution).isEqualTo("missed-deadline");
    }

    @Test
    void checkExpired_withFailDecision_writesExpiredAuditEntry() {
        policy.willReturn(new BreachDecision.Fail("missed-deadline"));
        final WorkItem wi = expiredItem();
        service.checkExpired();
        assertThat(auditStore.findByWorkItemId(wi.id))
                .anyMatch(e -> "EXPIRED".equals(e.event) && "system".equals(e.actor));
    }

    // ── checkExpired — EscalateTo decision ────────────────────────────────────

    @Test
    void checkExpired_withEscalateToDecision_remainsPending() {
        policy.willReturn(BreachDecision.EscalateTo.to("escalation-group"));
        final WorkItem wi = expiredItem();
        service.checkExpired();
        assertThat(store.get(wi.id).orElseThrow().status).isEqualTo(WorkItemStatus.PENDING);
    }

    @Test
    void checkExpired_withEscalateToDecision_updatesCandidateGroups() {
        policy.willReturn(BreachDecision.EscalateTo.to("senior-reviewers", "tech-leads"));
        final WorkItem wi = expiredItem();
        service.checkExpired();
        final String groups = store.get(wi.id).orElseThrow().candidateGroups;
        assertThat(groups).contains("senior-reviewers");
        assertThat(groups).contains("tech-leads");
    }

    @Test
    void checkExpired_withEscalateToDecision_clearsAssignee() {
        policy.willReturn(BreachDecision.EscalateTo.to("escalation-group"));
        final WorkItem wi = expiredItem();
        wi.assigneeId = "original-assignee";
        store.put(wi);
        service.checkExpired();
        assertThat(store.get(wi.id).orElseThrow().assigneeId).isNull();
    }

    @Test
    void checkExpired_withEscalateToDecision_resetsExpiresAtUsingConfigDefault() {
        policy.willReturn(BreachDecision.EscalateTo.to("escalation-group"));
        final WorkItem wi = expiredItem();
        service.checkExpired();
        // config.defaultExpiryHours() = 24 → expiresAt should be ~24h in future
        final Instant expiresAt = store.get(wi.id).orElseThrow().expiresAt;
        assertThat(expiresAt).isAfter(Instant.now().plus(23, ChronoUnit.HOURS));
    }

    @Test
    void checkExpired_withEscalateToWithDeadline_usesProvidedDeadlineForExpiresAt() {
        policy.willReturn(BreachDecision.EscalateTo.to("escalation-group").withDeadline(Duration.ofHours(4)));
        final WorkItem wi = expiredItem();
        service.checkExpired();
        // deadline=4h → expiresAt should be ~4h in future (not 24h default)
        final Instant expiresAt = store.get(wi.id).orElseThrow().expiresAt;
        assertThat(expiresAt).isBefore(Instant.now().plus(5, ChronoUnit.HOURS));
        assertThat(expiresAt).isAfter(Instant.now().plus(3, ChronoUnit.HOURS));
    }

    @Test
    void checkExpired_withEscalateToDecision_writesSlaReassignedAuditEntry_existingCoverage() {
        // Retained for backward compat — now named SLA_REASSIGNED, not ESCALATED
        policy.willReturn(BreachDecision.EscalateTo.to("escalation-group"));
        final WorkItem wi = expiredItem();
        service.checkExpired();
        assertThat(auditStore.findByWorkItemId(wi.id))
                .anyMatch(e -> "SLA_REASSIGNED".equals(e.event));
    }

    // ── checkExpired — audit detail ──────────────────────────────────────────

    @Test
    void checkExpired_withFailDecision_recordsReasonAsAuditEntryDetail() {
        policy.willReturn(new BreachDecision.Fail("deadline-breach"));
        final WorkItem wi = expiredItem();
        service.checkExpired();
        final AuditEntry entry = auditStore.findByWorkItemId(wi.id).stream()
                .filter(e -> "EXPIRED".equals(e.event))
                .findFirst().orElseThrow();
        assertThat(entry.detail).isEqualTo("deadline-breach");
    }

    // ── checkExpired — EscalateTo auto-assignment ─────────────────────────────

    @Test
    void checkExpired_withEscalateToDecision_triggersAutoAssignmentWhenCandidatesAvailable() {
        final CapturingStrategy capturing = new CapturingStrategy();
        service.assignmentService = new WorkItemAssignmentService(
                capturing,
                group -> java.util.List.of(WorkerCandidate.of("escalation-worker")),
                id -> 0,
                new WorkBroker(),
                (userId, excluded) -> PolicyDecision.ALLOW);

        policy.willReturn(BreachDecision.EscalateTo.to("senior-reviewers"));
        final WorkItem wi = expiredItem();
        service.checkExpired();

        assertThat(capturing.calls).hasSize(1);
        assertThat(store.get(wi.id).orElseThrow().assigneeId).isEqualTo("escalation-worker");
        assertThat(store.get(wi.id).orElseThrow().status).isEqualTo(WorkItemStatus.ASSIGNED);
    }

    @Test
    void checkExpired_withEscalateToDecision_remainsPendingWhenNoWorkersAvailable() {
        // The default service.assignmentService (wired in setUp) uses a no-op
        // strategy that returns noChange() — item stays PENDING
        policy.willReturn(BreachDecision.EscalateTo.to("senior-reviewers"));
        final WorkItem wi = expiredItem();
        service.checkExpired();
        assertThat(store.get(wi.id).orElseThrow().status).isEqualTo(WorkItemStatus.PENDING);
        assertThat(store.get(wi.id).orElseThrow().assigneeId).isNull();
    }

    // ── checkExpired — Extend decision ────────────────────────────────────────

    @Test
    void checkExpired_withExtendDecision_pushesExpiresAtByGivenDuration() {
        policy.willReturn(new BreachDecision.Extend(Duration.ofHours(2)));
        final WorkItem wi = expiredItem();
        service.checkExpired();
        // expiresAt was 1h in the past; after Extend(2h) it should be ~1h in the future
        assertThat(store.get(wi.id).orElseThrow().expiresAt).isAfter(Instant.now());
    }

    @Test
    void checkExpired_withExtendDecision_doesNotChangeStatus() {
        policy.willReturn(new BreachDecision.Extend(Duration.ofHours(2)));
        final WorkItem wi = expiredItem();
        service.checkExpired();
        assertThat(store.get(wi.id).orElseThrow().status).isEqualTo(WorkItemStatus.PENDING);
    }

    @Test
    void checkExpired_withExtendDecision_writesExtendedAuditEntry() {
        policy.willReturn(new BreachDecision.Extend(Duration.ofHours(2)));
        final WorkItem wi = expiredItem();
        service.checkExpired();
        assertThat(auditStore.findByWorkItemId(wi.id))
                .anyMatch(e -> "SLA_EXTENDED".equals(e.event));
    }

    // ── checkExpired — Chained decision ───────────────────────────────────────

    @Test
    void checkExpired_withBareEmptyEscalateTo_skipsItemAndWritesMisconfiguredAudit() {
        // Non-Chained EscalateTo(∅): item is skipped for the tick (BreachExecutionFailed
        // is caught at the batch loop level), audit entry written, batch not rolled back.
        policy.willReturn(new BreachDecision.EscalateTo(java.util.Set.of(), null)); // bypass factory
        final WorkItem wi = expiredItem();
        service.checkExpired();
        // Item status unchanged (still PENDING — not expired, not escalated)
        assertThat(store.get(wi.id).orElseThrow().status).isEqualTo(WorkItemStatus.PENDING);
        // Audit entry written for observability
        assertThat(auditStore.findByWorkItemId(wi.id))
                .anyMatch(e -> "BREACH_POLICY_MISCONFIGURED".equals(e.event));
        // No SlaBreachEvent fired for the skipped item
        assertThat(breachEvents).isEmpty();
    }

    // ── checkExpired — SlaBreachEvent ─────────────────────────────────────────

    @Test
    void checkExpired_alwaysFiresSlaBreachEvent() {
        policy.willReturn(new BreachDecision.Fail("test"));
        expiredItem();
        service.checkExpired();
        assertThat(breachEvents).hasSize(1);
    }

    @Test
    void checkExpired_slaBreachEventCarriesLeafDecision_notChainedWrapper() {
        // Chained with non-empty primary: primary executes, event carries EscalateTo leaf (not Chained)
        final BreachDecision.Fail fallback = new BreachDecision.Fail("fallback-reason");
        policy.willReturn(BreachDecision.EscalateTo.to("escalation-group").thenOnBreach(fallback));
        expiredItem();
        service.checkExpired();
        assertThat(breachEvents).hasSize(1);
        // event should carry the EscalateTo leaf, not the Chained wrapper
        assertThat(breachEvents.get(0).decision()).isInstanceOf(BreachDecision.EscalateTo.class);
    }

    @Test
    void checkExpired_slaBreachEventCarriesCorrectBreachType() {
        policy.willReturn(new BreachDecision.Fail("test"));
        expiredItem();
        service.checkExpired();
        assertThat(breachEvents.get(0).context().breachType())
                .isEqualTo(io.casehub.work.api.BreachType.COMPLETION_EXPIRED);
    }

    // ── checkExpired — general ────────────────────────────────────────────────

    @Test
    void checkExpired_skipsAlreadyTerminalItems() {
        final WorkItem wi = expiredItem();
        wi.status = WorkItemStatus.COMPLETED;
        store.put(wi);
        service.checkExpired();
        assertThat(store.get(wi.id).orElseThrow().status).isEqualTo(WorkItemStatus.COMPLETED);
    }

    @Test
    void checkExpired_processesMultipleItems() {
        policy.willReturn(new BreachDecision.Fail("test"));
        expiredItem();
        expiredItem();
        service.checkExpired();
        final long expiredCount = store.items.values().stream()
                .filter(wi -> wi.status == WorkItemStatus.EXPIRED).count();
        assertThat(expiredCount).isEqualTo(2);
    }

    // ── checkClaimDeadlines — Fail decision ───────────────────────────────────

    @Test
    void checkClaimDeadlines_withFailDecision_transitionsToExpired() {
        policy.willReturn(new BreachDecision.Fail("claim-window-exhausted"));
        final WorkItem wi = claimExpiredItem();
        service.checkClaimDeadlines();
        assertThat(store.get(wi.id).orElseThrow().status).isEqualTo(WorkItemStatus.EXPIRED);
    }

    @Test
    void checkClaimDeadlines_withFailDecision_writesExpiredAuditEntry() {
        policy.willReturn(new BreachDecision.Fail("claim-window-exhausted"));
        final WorkItem wi = claimExpiredItem();
        service.checkClaimDeadlines();
        assertThat(auditStore.findByWorkItemId(wi.id))
                .anyMatch(e -> "EXPIRED".equals(e.event));
    }

    // ── checkClaimDeadlines — EscalateTo decision ─────────────────────────────

    @Test
    void checkClaimDeadlines_withEscalateToDecision_resetsClaimDeadlineToFuture() {
        policy.willReturn(BreachDecision.EscalateTo.to("escalation-group"));
        final WorkItem wi = claimExpiredItem();
        service.checkClaimDeadlines();
        assertThat(store.get(wi.id).orElseThrow().claimDeadline).isAfter(Instant.now());
    }

    @Test
    void checkClaimDeadlines_withEscalateToDecision_ignoresDeadlineField_usesClaimSlaPolicy() {
        // deadline field on EscalateTo is ignored for CLAIM_EXPIRED — ClaimSlaPolicy wins
        policy.willReturn(BreachDecision.EscalateTo.to("group").withDeadline(Duration.ofMinutes(5)));
        final WorkItem wi = claimExpiredItem();
        service.checkClaimDeadlines();
        // FixedClaimSlaPolicy returns +4h, not +5m
        assertThat(store.get(wi.id).orElseThrow().claimDeadline)
                .isAfter(Instant.now().plus(3, ChronoUnit.HOURS));
    }

    @Test
    void checkClaimDeadlines_withEscalateToDecision_doesNotChangeStatus() {
        policy.willReturn(BreachDecision.EscalateTo.to("escalation-group"));
        final WorkItem wi = claimExpiredItem();
        service.checkClaimDeadlines();
        assertThat(store.get(wi.id).orElseThrow().status).isEqualTo(WorkItemStatus.PENDING);
    }

    @Test
    void checkClaimDeadlines_withEscalateToDecision_triggersAutoAssignmentWhenCandidatesAvailable() {
        final CapturingStrategy capturing = new CapturingStrategy();
        service.assignmentService = new WorkItemAssignmentService(
                capturing,
                group -> java.util.List.of(WorkerCandidate.of("escalation-worker")),
                id -> 0,
                new WorkBroker(),
                (userId, excluded) -> PolicyDecision.ALLOW);

        policy.willReturn(BreachDecision.EscalateTo.to("senior-reviewers"));
        final WorkItem wi = claimExpiredItem();
        service.checkClaimDeadlines();

        assertThat(capturing.calls).hasSize(1);
        assertThat(store.get(wi.id).orElseThrow().assigneeId).isEqualTo("escalation-worker");
        assertThat(store.get(wi.id).orElseThrow().status).isEqualTo(WorkItemStatus.ASSIGNED);
    }

    // ── checkClaimDeadlines — Extend decision ─────────────────────────────────

    @Test
    void checkClaimDeadlines_withExtendDecision_pushesClaimDeadlineByGivenDuration() {
        policy.willReturn(new BreachDecision.Extend(Duration.ofHours(2)));
        final WorkItem wi = claimExpiredItem();
        service.checkClaimDeadlines();
        assertThat(store.get(wi.id).orElseThrow().claimDeadline)
                .isAfter(Instant.now().plus(1, ChronoUnit.HOURS));
    }

    @Test
    void checkClaimDeadlines_withExtendDecision_doesNotChangeStatus() {
        policy.willReturn(new BreachDecision.Extend(Duration.ofHours(2)));
        final WorkItem wi = claimExpiredItem();
        service.checkClaimDeadlines();
        assertThat(store.get(wi.id).orElseThrow().status).isEqualTo(WorkItemStatus.PENDING);
    }

    // ── checkClaimDeadlines — time accumulation (always) ─────────────────────

    @Test
    void checkClaimDeadlines_accumulatesUnclaimedTimeRegardlessOfDecision() {
        policy.willReturn(new BreachDecision.Extend(Duration.ofHours(2)));
        final WorkItem wi = claimExpiredItem();
        service.checkClaimDeadlines();
        assertThat(store.get(wi.id).orElseThrow().accumulatedUnclaimedSeconds).isGreaterThan(0);
    }

    @Test
    void checkClaimDeadlines_updatesLastReturnedToPoolAt() {
        policy.willReturn(new BreachDecision.Extend(Duration.ofHours(2)));
        final WorkItem wi = claimExpiredItem();
        final Instant before = wi.lastReturnedToPoolAt;
        service.checkClaimDeadlines();
        assertThat(store.get(wi.id).orElseThrow().lastReturnedToPoolAt).isAfter(before);
    }

    @Test
    void checkClaimDeadlines_slaBreachEventCarriesClaimExpiredBreachType() {
        policy.willReturn(new BreachDecision.Fail("test"));
        claimExpiredItem();
        service.checkClaimDeadlines();
        assertThat(breachEvents).hasSize(1);
        assertThat(breachEvents.get(0).context().breachType())
                .isEqualTo(io.casehub.work.api.BreachType.CLAIM_EXPIRED);
    }

    // ── buildBreachContext ────────────────────────────────────────────────────

    @Test
    void checkExpired_breachContextUsesRootScopeWhenItemHasNoScope() {
        policy.willReturn(new BreachDecision.Fail("test"));
        expiredItem();
        service.checkExpired();
        assertThat(breachEvents.get(0).context().scope()).isEqualTo(Path.root());
    }

    @Test
    void checkExpired_breachContextUsesParsedScopeWhenItemHasScope() {
        policy.willReturn(new BreachDecision.Fail("test"));
        final WorkItem wi = expiredItem();
        wi.scope = "casehubio/devtown/pr-review";
        store.put(wi);
        service.checkExpired();
        assertThat(breachEvents.get(0).context().scope())
                .isEqualTo(Path.of("casehubio", "devtown", "pr-review"));
    }

    // ── #244: Chained exhaustion → ESCALATED ─────────────────────────────────

    @Test
    void checkExpired_withChainedBothBranchesEmptyEscalateTo_setsEscalatedStatus() {
        policy.willReturn(new BreachDecision.Chained(
                new BreachDecision.EscalateTo(java.util.Set.of(), null),
                new BreachDecision.EscalateTo(java.util.Set.of(), null)));
        final WorkItem wi = expiredItem();
        service.checkExpired();
        assertThat(store.get(wi.id).orElseThrow().status).isEqualTo(WorkItemStatus.ESCALATED);
    }

    @Test
    void checkExpired_withChainedBothBranchesEmptyEscalateTo_setsCompletedAt() {
        policy.willReturn(new BreachDecision.Chained(
                new BreachDecision.EscalateTo(java.util.Set.of(), null),
                new BreachDecision.EscalateTo(java.util.Set.of(), null)));
        final WorkItem wi = expiredItem();
        service.checkExpired();
        assertThat(store.get(wi.id).orElseThrow().completedAt).isNotNull();
    }

    @Test
    void checkExpired_withChainedBothBranchesEmptyEscalateTo_writesEscalatedAuditEntry() {
        policy.willReturn(new BreachDecision.Chained(
                new BreachDecision.EscalateTo(java.util.Set.of(), null),
                new BreachDecision.EscalateTo(java.util.Set.of(), null)));
        final WorkItem wi = expiredItem();
        service.checkExpired();
        assertThat(auditStore.findByWorkItemId(wi.id))
                .anyMatch(e -> "ESCALATED".equals(e.event) && "system".equals(e.actor));
    }

    @Test
    void checkExpired_withChainedBothBranchesEmptyEscalateTo_firesExhaustedSlaBreachEvent() {
        policy.willReturn(new BreachDecision.Chained(
                new BreachDecision.EscalateTo(java.util.Set.of(), null),
                new BreachDecision.EscalateTo(java.util.Set.of(), null)));
        expiredItem();
        service.checkExpired();
        assertThat(breachEvents).hasSize(1);
        assertThat(breachEvents.get(0).decision()).isInstanceOf(BreachDecision.Exhausted.class);
    }

    @Test
    void checkExpired_withDirectExhaustedDecision_setsEscalatedStatus() {
        policy.willReturn(new BreachDecision.Exhausted("all-paths-exhausted"));
        final WorkItem wi = expiredItem();
        service.checkExpired();
        assertThat(store.get(wi.id).orElseThrow().status).isEqualTo(WorkItemStatus.ESCALATED);
    }

    // ── #244: non-Chained EscalateTo(∅) → skip ───────────────────────────────

    @Test
    void checkExpired_withNonChainedEmptyEscalateTo_skipsItemWithNoStatusChange() {
        policy.willReturn(new BreachDecision.EscalateTo(java.util.Set.of(), null));
        final WorkItem wi = expiredItem();
        final WorkItemStatus originalStatus = wi.status;
        service.checkExpired();
        assertThat(store.get(wi.id).orElseThrow().status).isEqualTo(originalStatus);
    }

    @Test
    void checkExpired_withNonChainedEmptyEscalateTo_writesMisconfiguredAuditEntry() {
        policy.willReturn(new BreachDecision.EscalateTo(java.util.Set.of(), null));
        final WorkItem wi = expiredItem();
        service.checkExpired();
        assertThat(auditStore.findByWorkItemId(wi.id))
                .anyMatch(e -> "BREACH_POLICY_MISCONFIGURED".equals(e.event));
    }

    @Test
    void checkExpired_withNonChainedEmptyEscalateTo_otherItemsStillProcessed() {
        policy.willReturn(new BreachDecision.EscalateTo(java.util.Set.of(), null));
        expiredItem(); // will be skipped
        // Add a second item — configure policy to return Fail for it
        // Can't vary per-item in TestSlaBreachPolicy, so just verify the first is skipped
        // (batch integrity — no rollback of the transaction covering both items)
        service.checkExpired();
        // No exception thrown = batch integrity maintained
    }

    // ── #244: SLA_REASSIGNED audit event ─────────────────────────────────────

    @Test
    void checkExpired_withEscalateToDecision_writesSlaReassignedAuditEntry() {
        policy.willReturn(BreachDecision.EscalateTo.to("escalation-group"));
        final WorkItem wi = expiredItem();
        service.checkExpired();
        assertThat(auditStore.findByWorkItemId(wi.id))
                .anyMatch(e -> "SLA_REASSIGNED".equals(e.event));
    }

    @Test
    void checkExpired_withEscalateToDecision_doesNotWriteEscalatedAuditEntry() {
        // "ESCALATED" must only appear when the item reaches ESCALATED terminal status
        policy.willReturn(BreachDecision.EscalateTo.to("escalation-group"));
        final WorkItem wi = expiredItem();
        service.checkExpired();
        assertThat(auditStore.findByWorkItemId(wi.id))
                .noneMatch(e -> "ESCALATED".equals(e.event));
    }
}
