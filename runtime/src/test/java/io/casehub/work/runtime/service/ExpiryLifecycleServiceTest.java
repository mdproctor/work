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
    void checkExpired_withEscalateToDecision_writesEscalatedAuditEntry() {
        policy.willReturn(BreachDecision.EscalateTo.to("escalation-group"));
        final WorkItem wi = expiredItem();
        service.checkExpired();
        assertThat(auditStore.findByWorkItemId(wi.id))
                .anyMatch(e -> "ESCALATED".equals(e.event));
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
    void checkExpired_withBareEmptyEscalateTo_convertsToFailWithoutThrowingOrRollingBack() {
        // Fix B: executor converts EscalateTo(∅) to Fail instead of throwing BreachExecutionFailed.
        // Bypasses factory validation (which would reject this at construction) to test the
        // belt-and-suspenders protection at the executor level.
        policy.willReturn(new BreachDecision.EscalateTo(java.util.Set.of(), null)); // bypass factory
        final WorkItem wi = expiredItem();
        service.checkExpired();
        assertThat(store.get(wi.id).orElseThrow().status).isEqualTo(WorkItemStatus.EXPIRED);
        assertThat(store.get(wi.id).orElseThrow().resolution).isEqualTo("escalation-misconfigured");
        assertThat(breachEvents).hasSize(1);
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
}
