package io.casehub.work.runtime.strategy;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.UUID;

import jakarta.inject.Inject;

import org.junit.jupiter.api.Test;

import io.casehub.work.core.strategy.RoutingCursorStore;
import io.casehub.work.runtime.model.RoutingCursor;
import io.quarkus.test.junit.QuarkusTest;

/**
 * Verifies that {@link io.casehub.work.runtime.repository.jpa.JpaRoutingCursorStore}
 * stamps {@code lastAccessed} on every cursor acquisition.
 */
@QuarkusTest
class RoutingCursorLastAccessedTest {

    @Inject
    RoutingCursorStore cursorStore;

    @Test
    void acquireNext_stampsLastAccessed() {
        final String poolHash = "last-accessed-" + UUID.randomUUID();
        final Instant before = Instant.now().minusSeconds(1);

        cursorStore.acquireNext(poolHash, 3);

        final RoutingCursor cursor = RoutingCursor.findById(poolHash);
        assertThat(cursor).isNotNull();
        assertThat(cursor.lastAccessed).isNotNull();
        assertThat(cursor.lastAccessed).isAfter(before);
    }
}
