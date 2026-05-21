package io.casehub.work.runtime.strategy;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;

import jakarta.inject.Inject;

import org.junit.jupiter.api.Test;

import io.casehub.work.core.strategy.RoutingCursorStore;
import io.quarkus.test.junit.QuarkusTest;

/**
 * Verifies {@link RoutingCursorStore} behaviour in a Quarkus CDI context against H2.
 *
 * <p>
 * Each test uses a UUID-keyed pool hash to avoid cursor state bleed between test cases.
 */
@QuarkusTest
class RoundRobinIntegrationTest {

    @Inject
    RoutingCursorStore cursorStore;

    @Test
    void acquireNext_advancesCursorSequentially() {
        final String poolHash = "sequential-" + UUID.randomUUID();
        assertThat(cursorStore.acquireNext(poolHash, 3)).isEqualTo(0);
        assertThat(cursorStore.acquireNext(poolHash, 3)).isEqualTo(1);
        assertThat(cursorStore.acquireNext(poolHash, 3)).isEqualTo(2);
        assertThat(cursorStore.acquireNext(poolHash, 3)).isEqualTo(0);
    }

    @Test
    void acquireNext_differentPools_independentCursors() {
        final String poolA = "pool-a-" + UUID.randomUUID();
        final String poolB = "pool-b-" + UUID.randomUUID();
        assertThat(cursorStore.acquireNext(poolA, 2)).isEqualTo(0);
        assertThat(cursorStore.acquireNext(poolB, 2)).isEqualTo(0);
        assertThat(cursorStore.acquireNext(poolA, 2)).isEqualTo(1);
        assertThat(cursorStore.acquireNext(poolB, 2)).isEqualTo(1);
    }
}
