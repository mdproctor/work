package io.casehub.work.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import io.casehub.platform.api.path.Path;
import io.casehub.platform.api.preferences.MapPreferences;

import org.junit.jupiter.api.Test;

class SlaBreachPolicyTest {

    // ── BreachDecision.EscalateTo factory ─────────────────────────────────────

    @Test
    void escalateTo_factoryCreatesCorrectGroups() {
        final BreachDecision.EscalateTo d = BreachDecision.EscalateTo.to("senior-reviewers", "tech-leads");
        assertThat(d.groups()).containsExactlyInAnyOrder("senior-reviewers", "tech-leads");
    }

    @Test
    void escalateTo_factoryRejectsEmptyGroups() {
        assertThatThrownBy(() -> BreachDecision.EscalateTo.to())
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void escalateTo_factoryHasNullDeadline() {
        final BreachDecision.EscalateTo d = BreachDecision.EscalateTo.to("group-a");
        assertThat(d.deadline()).isNull();
    }

    @Test
    void escalateTo_withDeadlineReturnsCopyWithDeadline() {
        final BreachDecision.EscalateTo base = BreachDecision.EscalateTo.to("group-a");
        final BreachDecision.EscalateTo withDl = base.withDeadline(Duration.ofHours(4));
        assertThat(withDl.groups()).isEqualTo(base.groups());
        assertThat(withDl.deadline()).isEqualTo(Duration.ofHours(4));
        assertThat(base.deadline()).isNull(); // base unchanged
    }

    // ── thenOnBreach chaining ─────────────────────────────────────────────────

    @Test
    void thenOnBreach_constructsChainedWithCorrectPrimaryAndFallback() {
        final BreachDecision primary = BreachDecision.EscalateTo.to("escalation-group");
        final BreachDecision fallback = new BreachDecision.Fail("no-escalation-target");
        final BreachDecision chained = primary.thenOnBreach(fallback);

        assertThat(chained).isInstanceOf(BreachDecision.Chained.class);
        final BreachDecision.Chained c = (BreachDecision.Chained) chained;
        assertThat(c.primary()).isSameAs(primary);
        assertThat(c.fallback()).isSameAs(fallback);
    }

    @Test
    void thenOnBreach_chainingIsComposable() {
        final BreachDecision tier1 = BreachDecision.EscalateTo.to("tier1");
        final BreachDecision tier2 = BreachDecision.EscalateTo.to("tier2");
        final BreachDecision terminal = new BreachDecision.Fail("exhausted");

        final BreachDecision chain = tier1.thenOnBreach(tier2.thenOnBreach(terminal));

        assertThat(chain).isInstanceOf(BreachDecision.Chained.class);
        final BreachDecision.Chained outer = (BreachDecision.Chained) chain;
        assertThat(outer.primary()).isSameAs(tier1);
        assertThat(outer.fallback()).isInstanceOf(BreachDecision.Chained.class);
    }

    // ── BreachDecision sealed permits ─────────────────────────────────────────

    @Test
    void breachDecision_patternMatchCoversAllPermits() {
        // If a new permit is added without updating this test, the switch will fail to compile
        final BreachDecision[] all = {
            new BreachDecision.Fail("reason"),
            BreachDecision.EscalateTo.to("g"),
            new BreachDecision.Extend(Duration.ofHours(1)),
            new BreachDecision.Chained(new BreachDecision.Fail("p"), new BreachDecision.Fail("f"))
        };
        for (final BreachDecision d : all) {
            final String kind = switch (d) {
                case BreachDecision.Fail f -> "fail";
                case BreachDecision.EscalateTo e -> "escalate";
                case BreachDecision.Extend ex -> "extend";
                case BreachDecision.Chained ch -> "chained";
            };
            assertThat(kind).isNotBlank();
        }
    }

    // ── SlaBreachContext accessors ────────────────────────────────────────────

    @Test
    void slaBreachContext_accessorsReturnConstructorArgs() {
        final UUID id = UUID.randomUUID();
        final BreachedTask task = new BreachedTask(id, "case:x/pi:y", "Review PR", Set.of("reviewers"));
        assertThat(task.taskId()).isEqualTo(id);
        assertThat(task.callerRef()).isEqualTo("case:x/pi:y");
        assertThat(task.title()).isEqualTo("Review PR");
        assertThat(task.candidateGroups()).containsExactly("reviewers");
    }

    // ── BreachType ────────────────────────────────────────────────────────────

    @Test
    void breachType_hasBothValues() {
        assertThat(BreachType.values()).containsExactlyInAnyOrder(
                BreachType.CLAIM_EXPIRED, BreachType.COMPLETION_EXPIRED);
    }

    // ── Devtown two-tier policy pattern (acceptance test) ─────────────────────

    @Test
    void devtownPolicyPattern_escalatesOnFirstBreachFailsOnSecond() {
        // Stateless two-tier policy: if escalation group already in candidateGroups → Fail
        final String escalationGroup = "senior-reviewers";
        final SlaBreachPolicy policy = ctx -> {
            if (ctx.task().candidateGroups().contains(escalationGroup)) {
                return new BreachDecision.Fail("sla-exhausted");
            }
            return BreachDecision.EscalateTo.to(escalationGroup).withDeadline(Duration.ofHours(4));
        };

        // First breach: escalate
        final BreachedTask first = new BreachedTask(UUID.randomUUID(), null, "PR review", Set.of("reviewers"));
        final BreachDecision d1 = policy.onBreach(makeCtx(BreachType.COMPLETION_EXPIRED, first));
        assertThat(d1).isInstanceOf(BreachDecision.EscalateTo.class);
        assertThat(((BreachDecision.EscalateTo) d1).groups()).containsExactly(escalationGroup);
        assertThat(((BreachDecision.EscalateTo) d1).deadline()).isEqualTo(Duration.ofHours(4));

        // Second breach: already at escalation group → terminal
        final BreachedTask second = new BreachedTask(UUID.randomUUID(), null, "PR review", Set.of(escalationGroup));
        final BreachDecision d2 = policy.onBreach(makeCtx(BreachType.COMPLETION_EXPIRED, second));
        assertThat(d2).isInstanceOf(BreachDecision.Fail.class);
        assertThat(((BreachDecision.Fail) d2).reason()).isEqualTo("sla-exhausted");
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private static SlaBreachContext makeCtx(final BreachType type, final BreachedTask task) {
        return new SlaBreachContext(type, task, Path.root(), new MapPreferences(Map.of()));
    }
}
