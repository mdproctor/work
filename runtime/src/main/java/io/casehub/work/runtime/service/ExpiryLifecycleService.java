package io.casehub.work.runtime.service;

import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Event;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

import io.casehub.platform.api.path.Path;
import io.casehub.platform.api.preferences.PreferenceProvider;
import io.casehub.platform.api.preferences.SettingsScope;
import io.casehub.work.api.BreachDecision;
import io.casehub.work.api.BreachType;
import io.casehub.work.api.BreachedTask;
import io.casehub.work.api.ClaimSlaContext;
import io.casehub.work.api.ClaimSlaPolicy;
import io.casehub.work.api.SlaBreachContext;
import io.casehub.work.api.SlaBreachPolicy;
import io.casehub.work.runtime.config.WorkItemsConfig;
import io.casehub.work.runtime.event.SlaBreachEvent;
import io.casehub.work.runtime.event.WorkItemLifecycleEvent;
import io.casehub.work.runtime.model.AuditEntry;
import io.casehub.work.runtime.model.WorkItem;
import io.casehub.work.runtime.model.WorkItemStatus;
import io.casehub.work.runtime.repository.AuditEntryStore;
import io.casehub.work.runtime.repository.WorkItemQuery;
import io.casehub.work.runtime.repository.WorkItemStore;

/**
 * Handles expiry evaluation, SLA breach policy dispatch, and claim deadline breach processing.
 * Called by {@link ExpiryCleanupJob} and {@link ClaimDeadlineJob}; also provides
 * {@link #computeNewClaimDeadline} for use by lifecycle transitions that return a
 * WorkItem to the pool (release, delegate).
 */
@ApplicationScoped
public class ExpiryLifecycleService {

    @Inject
    WorkItemStore workItemStore;

    @Inject
    AuditEntryStore auditStore;

    @Inject
    SlaBreachPolicy slaBreachPolicy;

    @Inject
    PreferenceProvider preferenceProvider;

    @Inject
    Event<WorkItemLifecycleEvent> lifecycleEvent;

    @Inject
    Event<SlaBreachEvent> slaBreachEventBus;

    @Inject
    ClaimSlaPolicy claimSlaPolicy;

    @Inject
    WorkItemsConfig config;

    /**
     * Marks all WorkItems whose {@code expiresAt} has passed and delegates the
     * breach decision to {@link SlaBreachPolicy}.
     * Called by {@link ExpiryCleanupJob} on each scheduled tick.
     */
    @Transactional
    public void checkExpired() {
        final Instant now = Instant.now();
        for (final WorkItem item : workItemStore.scan(WorkItemQuery.expired(now))) {
            final SlaBreachContext ctx = buildBreachContext(item, BreachType.COMPLETION_EXPIRED, now);
            final BreachDecision leaf = executeBreachDecision(item, slaBreachPolicy.onBreach(ctx), ctx, now);
            slaBreachEventBus.fire(new SlaBreachEvent(ctx, leaf));
        }
    }

    /**
     * Processes WorkItems whose {@code claimDeadline} has passed — accumulates unclaimed time,
     * then delegates the breach decision to {@link SlaBreachPolicy}.
     * Called by {@link ClaimDeadlineJob} on each scheduled tick.
     */
    @Transactional
    public void checkClaimDeadlines() {
        final Instant now = Instant.now();
        for (final WorkItem item : workItemStore.scan(WorkItemQuery.claimExpired(now))) {
            // Time accumulation always happens, regardless of policy decision
            if (item.lastReturnedToPoolAt != null) {
                item.accumulatedUnclaimedSeconds += Duration.between(item.lastReturnedToPoolAt, now).toSeconds();
            }
            item.lastReturnedToPoolAt = now;

            // CLAIM_EXPIRED lifecycle event is a factual record of the deadline passing —
            // fire it unconditionally before executing the policy decision.
            fireLifecycleEvent("CLAIM_EXPIRED", item);

            final SlaBreachContext ctx = buildBreachContext(item, BreachType.CLAIM_EXPIRED, now);
            final BreachDecision leaf = executeBreachDecision(item, slaBreachPolicy.onBreach(ctx), ctx, now);
            slaBreachEventBus.fire(new SlaBreachEvent(ctx, leaf));
        }
    }

    /**
     * Computes the next claim deadline for a WorkItem that has returned to the pool.
     * Used by release and delegate transitions in {@link WorkItemService}.
     */
    public Instant computeNewClaimDeadline(final WorkItem item, final Instant now) {
        return claimSlaPolicy.computePoolDeadline(buildClaimSlaContext(item, now));
    }

    // ── Decision execution ───────────────────────────────────────────────────

    /**
     * Executes the given {@link BreachDecision} against {@code item} and returns the
     * leaf decision that actually ran. For {@link BreachDecision.Chained}, returns
     * whichever branch executed. Never returns a {@link BreachDecision.Chained}.
     */
    private BreachDecision executeBreachDecision(
            final WorkItem item, final BreachDecision decision,
            final SlaBreachContext ctx, final Instant now) {
        return switch (decision) {
            case BreachDecision.Fail fail -> executeFail(item, fail, now);
            case BreachDecision.EscalateTo escalate -> executeEscalateTo(item, escalate, ctx, now);
            case BreachDecision.Extend extend -> executeExtend(item, extend, ctx, now);
            case BreachDecision.Chained chained -> {
                try {
                    yield executeBreachDecision(item, chained.primary(), ctx, now);
                } catch (final BreachExecutionFailed e) {
                    yield executeBreachDecision(item, chained.fallback(), ctx, now);
                }
            }
        };
    }

    private BreachDecision.Fail executeFail(final WorkItem item, final BreachDecision.Fail fail, final Instant now) {
        item.status = WorkItemStatus.EXPIRED;
        item.completedAt = now;
        item.resolution = fail.reason();
        workItemStore.put(item);
        writeAudit(item, "EXPIRED", now);
        fireLifecycleEvent("EXPIRED", item);
        return fail;
    }

    private BreachDecision.EscalateTo executeEscalateTo(
            final WorkItem item, final BreachDecision.EscalateTo escalate,
            final SlaBreachContext ctx, final Instant now) {
        if (escalate.groups().isEmpty()) {
            throw new BreachExecutionFailed("EscalateTo with empty groups cannot be executed");
        }
        item.candidateGroups = String.join(",", escalate.groups());
        item.assigneeId = null;
        item.status = WorkItemStatus.PENDING;

        if (ctx.breachType() == BreachType.COMPLETION_EXPIRED) {
            final Duration window = escalate.deadline() != null
                    ? escalate.deadline()
                    : Duration.ofHours(config.defaultExpiryHours());
            item.expiresAt = now.plus(window);
        } else {
            // CLAIM_EXPIRED: deadline field ignored — ClaimSlaPolicy governs the new window
            item.claimDeadline = computeNewClaimDeadline(item, now);
        }

        workItemStore.put(item);
        writeAudit(item, "ESCALATED", now);
        fireLifecycleEvent("ESCALATED", item);
        return escalate;
    }

    private BreachDecision.Extend executeExtend(
            final WorkItem item, final BreachDecision.Extend extend,
            final SlaBreachContext ctx, final Instant now) {
        if (ctx.breachType() == BreachType.COMPLETION_EXPIRED) {
            item.expiresAt = now.plus(extend.by());
        } else {
            item.claimDeadline = now.plus(extend.by());
        }
        workItemStore.put(item);
        writeAudit(item, "SLA_EXTENDED", now);
        // No lifecycle event — deadline extension is not a status transition
        return extend;
    }

    // ── Context construction ──────────────────────────────────────────────────

    private SlaBreachContext buildBreachContext(final WorkItem item, final BreachType type, final Instant now) {
        final Path scope = item.scope != null ? Path.parse(item.scope) : Path.root();
        final var prefs = preferenceProvider.resolve(new SettingsScope(scope, now));
        final Set<String> groups = parseCandidateGroups(item.candidateGroups);
        final BreachedTask task = new BreachedTask(item.id.toString(), item.callerRef, item.title, groups);
        return new SlaBreachContext(type, task, scope, prefs);
    }

    private ClaimSlaContext buildClaimSlaContext(final WorkItem item, final Instant now) {
        final Duration totalPoolSla = config.defaultClaimHours() > 0
                ? Duration.ofHours(config.defaultClaimHours())
                : Duration.ofHours(24);
        final Duration accumulated = Duration.ofSeconds(item.accumulatedUnclaimedSeconds);
        final Instant submitted = item.createdAt != null ? item.createdAt : now;
        return new ClaimSlaContext(submitted, totalPoolSla, accumulated, now);
    }

    private static Set<String> parseCandidateGroups(final String csv) {
        if (csv == null || csv.isBlank()) return Set.of();
        return Arrays.stream(csv.split(","))
                .map(String::strip)
                .filter(s -> !s.isEmpty())
                .collect(Collectors.toSet());
    }

    // ── Audit and events ──────────────────────────────────────────────────────

    private void writeAudit(final WorkItem item, final String event, final Instant now) {
        final AuditEntry entry = new AuditEntry();
        entry.workItemId = item.id;
        entry.event = event;
        entry.actor = "system";
        entry.occurredAt = now;
        auditStore.append(entry);
    }

    private void fireLifecycleEvent(final String event, final WorkItem item) {
        if (lifecycleEvent != null) {
            lifecycleEvent.fire(WorkItemLifecycleEvent.of(event, item, "system", null));
        }
    }

    // ── Internal ─────────────────────────────────────────────────────────────

    /** Thrown when an {@link BreachDecision.EscalateTo} cannot execute; caught by Chained handler only. */
    private static final class BreachExecutionFailed extends RuntimeException {
        BreachExecutionFailed(final String msg) { super(msg, null, true, false); }
    }
}
