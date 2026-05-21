package io.casehub.work.core.strategy;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.casehub.work.api.AssignmentDecision;
import io.casehub.work.api.SelectionContext;
import io.casehub.work.api.WorkerCandidate;

class RoundRobinStrategyTest {

    static class MapCursorStore implements RoutingCursorStore {
        private final Map<String, Integer> cursors = new HashMap<>();

        @Override
        public int acquireNext(final String poolHash, final int poolSize) {
            final int next = (cursors.getOrDefault(poolHash, -1) + 1) % poolSize;
            cursors.put(poolHash, next);
            return next;
        }
    }

    private RoundRobinStrategy strategy;

    @BeforeEach
    void setUp() {
        strategy = new RoundRobinStrategy(new MapCursorStore());
    }

    private static SelectionContext ctx() {
        return new SelectionContext(null, null, null, null, null, null, null, null);
    }

    private static WorkerCandidate candidate(final String id) {
        return new WorkerCandidate(id, Set.of(), 0);
    }

    @Test
    void select_rotatesAcrossCandidatesInOrder() {
        final List<WorkerCandidate> candidates = List.of(
                candidate("alice"), candidate("bob"), candidate("carol"));

        assertThat(strategy.select(ctx(), candidates).assigneeId()).isEqualTo("alice");
        assertThat(strategy.select(ctx(), candidates).assigneeId()).isEqualTo("bob");
        assertThat(strategy.select(ctx(), candidates).assigneeId()).isEqualTo("carol");
        assertThat(strategy.select(ctx(), candidates).assigneeId()).isEqualTo("alice");
    }

    @Test
    void select_emptyCandidates_returnsNoChange() {
        assertThat(strategy.select(ctx(), List.of()).isNoOp()).isTrue();
    }

    @Test
    void select_singleCandidate_alwaysReturnsSame() {
        final List<WorkerCandidate> candidates = List.of(candidate("alice"));
        for (int i = 0; i < 5; i++) {
            assertThat(strategy.select(ctx(), candidates).assigneeId()).isEqualTo("alice");
        }
    }

    @Test
    void select_differentPools_maintainSeparateCursors() {
        final List<WorkerCandidate> pool1 = List.of(candidate("alice"), candidate("bob"));
        final List<WorkerCandidate> pool2 = List.of(candidate("carol"), candidate("dave"));

        assertThat(strategy.select(ctx(), pool1).assigneeId()).isEqualTo("alice");
        assertThat(strategy.select(ctx(), pool2).assigneeId()).isEqualTo("carol");
        assertThat(strategy.select(ctx(), pool1).assigneeId()).isEqualTo("bob");
        assertThat(strategy.select(ctx(), pool2).assigneeId()).isEqualTo("dave");
    }

    @Test
    void select_candidateListOrderDoesNotAffectPoolHash() {
        // Same candidates in different order → same pool hash → shared cursor
        final List<WorkerCandidate> forward = List.of(
                candidate("alice"), candidate("bob"), candidate("carol"));
        final List<WorkerCandidate> reversed = List.of(
                candidate("carol"), candidate("bob"), candidate("alice"));

        // First call with forward order selects index 0
        final String id1 = strategy.select(ctx(), forward).assigneeId();
        assertThat(id1).isEqualTo("alice");

        // Second call with reversed order uses same cursor (same pool hash)
        // cursor increments to index 1, reversed[1] is "bob"
        final String id2 = strategy.select(ctx(), reversed).assigneeId();
        assertThat(id2).isEqualTo("bob");

        // Third call with forward order: cursor at index 2, forward[2] is "carol"
        final String id3 = strategy.select(ctx(), forward).assigneeId();
        assertThat(id3).isEqualTo("carol");

        // Fourth call wraps around: cursor at index 0
        final String id4 = strategy.select(ctx(), reversed).assigneeId();
        assertThat(id4).isEqualTo("carol");
    }
}
