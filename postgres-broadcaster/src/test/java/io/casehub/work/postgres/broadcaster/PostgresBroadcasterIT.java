package io.casehub.work.postgres.broadcaster;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

import jakarta.enterprise.event.Event;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

import org.awaitility.Awaitility;
import org.junit.jupiter.api.Test;

import io.casehub.work.runtime.event.WorkItemEventBroadcaster;
import io.casehub.work.runtime.event.WorkItemLifecycleEvent;
import io.casehub.work.runtime.model.WorkItem;
import io.casehub.work.runtime.model.WorkItemStatus;
import io.casehub.work.runtime.service.WorkItemService;
import io.casehub.work.runtime.model.WorkItemCreateRequest;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;

/**
 * Integration tests for {@link PostgresWorkItemEventBroadcaster} against a real
 * PostgreSQL container. Verifies the full LISTEN/NOTIFY path:
 * publish via pg_notify → receive via subscription → emit to SSE stream.
 *
 * <h2>Test structure</h2>
 * <ul>
 *   <li><b>Wiring:</b> CDI selects PostgresWorkItemEventBroadcaster over LocalWorkItemEventBroadcaster
 *   <li><b>Happy path:</b> lifecycle event reaches SSE stream via PostgreSQL channel
 *   <li><b>Correctness:</b> filter by workItemId and type work end-to-end
 *   <li><b>Robustness:</b> AFTER_SUCCESS semantics — rolled-back events do NOT appear
 * </ul>
 */
@QuarkusTest
@QuarkusTestResource(PostgresBroadcasterTestResource.class)
class PostgresBroadcasterIT {

    @Inject
    WorkItemEventBroadcaster broadcaster;

    @Inject
    WorkItemService workItemService;

    @Inject
    Event<WorkItemLifecycleEvent> lifecycleEvents;

    // ── Wiring ────────────────────────────────────────────────────────────────

    @Test
    void broadcaster_isPostgresImpl() {
        assertThat(broadcaster).isInstanceOf(PostgresWorkItemEventBroadcaster.class);
    }

    // ── Happy path — end-to-end delivery ──────────────────────────────────────

    @Test
    void workItemCreated_eventReachesStream() {
        final List<WorkItemLifecycleEvent> received = new CopyOnWriteArrayList<>();

        broadcaster.stream(null, null).subscribe().with(received::add);

        createWorkItem("SSE fan-out test");

        Awaitility.await().atMost(Duration.ofSeconds(5))
                .untilAsserted(() -> assertThat(received)
                        .anyMatch(e -> e.type().endsWith("created")));
    }

    @Test
    void workItemCreated_eventContainsCorrectWorkItemId() {
        final List<WorkItemLifecycleEvent> received = new CopyOnWriteArrayList<>();

        broadcaster.stream(null, null).subscribe().with(received::add);

        final WorkItem wi = createWorkItem("ID fidelity test");

        Awaitility.await().atMost(Duration.ofSeconds(5))
                .untilAsserted(() -> assertThat(received)
                        .anyMatch(e -> wi.id.equals(e.workItemId())));
    }

    @Test
    void multipleEvents_allReachStream() {
        final List<WorkItemLifecycleEvent> received = new CopyOnWriteArrayList<>();

        broadcaster.stream(null, null).subscribe().with(received::add);

        createWorkItem("Event 1");
        createWorkItem("Event 2");
        createWorkItem("Event 3");

        Awaitility.await().atMost(Duration.ofSeconds(8))
                .untilAsserted(() -> assertThat(received.stream()
                        .filter(e -> e.type().endsWith("created")).count()).isGreaterThanOrEqualTo(3));
    }

    // ── Correctness — filter by workItemId ────────────────────────────────────

    @Test
    void stream_filterByWorkItemId_onlyTargetIdDelivered() {
        // Warm-up: subscribe to the unfiltered stream before creating the target,
        // then wait for the target's CREATED event to arrive. This confirms the
        // LISTEN channel is established and the async notify→receive path is working
        // before we subscribe to the filtered stream and assert on it.
        // Without this, @PostConstruct startListening() may not have completed its
        // async LISTEN command in CI before the test fires events.
        final List<WorkItemLifecycleEvent> warmup = new CopyOnWriteArrayList<>();
        broadcaster.stream(null, null).subscribe().with(warmup::add);

        final WorkItem target = createWorkItem("Target item");
        Awaitility.await().atMost(Duration.ofSeconds(10))
                .until(() -> warmup.stream().anyMatch(e -> target.id.equals(e.workItemId())));

        // LISTEN channel confirmed active. Now subscribe to the filtered stream.
        final List<WorkItemLifecycleEvent> received = new CopyOnWriteArrayList<>();
        broadcaster.stream(target.id, null).subscribe().with(received::add);

        createWorkItem("Noise item 1");
        createWorkItem("Noise item 2");

        claimWorkItem(target, "alice");
        Awaitility.await().atMost(Duration.ofSeconds(10))
                .untilAsserted(() -> {
                    assertThat(received).isNotEmpty();
                    assertThat(received).allMatch(e -> target.id.equals(e.workItemId()));
                });
    }

    // ── Correctness — filter by type ──────────────────────────────────────────

    @Test
    void stream_filterByType_onlyMatchingTypeDelivered() {
        final List<WorkItemLifecycleEvent> received = new CopyOnWriteArrayList<>();

        broadcaster.stream(null, "assigned").subscribe().with(received::add);

        final WorkItem wi = createWorkItem("Type filter test");
        claimWorkItem(wi, "alice");

        Awaitility.await().atMost(Duration.ofSeconds(5))
                .untilAsserted(() -> {
                    assertThat(received).isNotEmpty();
                    assertThat(received).allMatch(e -> e.type().endsWith("assigned"));
                });
    }

    // ── Robustness — AFTER_SUCCESS semantics ──────────────────────────────────

    @Test
    void rolledBackTransaction_eventDoesNotReachStream() throws InterruptedException {
        final List<WorkItemLifecycleEvent> received = new CopyOnWriteArrayList<>();

        broadcaster.stream(null, "rollback_marker").subscribe().with(received::add);

        // Fire an event with a synthetic type inside a transaction that is then rolled back.
        // AFTER_SUCCESS means the pg_notify only fires after commit — a rollback suppresses it.
        try {
            fireEventInRolledBackTransaction();
        } catch (final RuntimeException ignored) {
            // expected — transaction rolls back
        }

        // Give the channel time to deliver if it was (incorrectly) published
        Thread.sleep(500);
        assertThat(received).isEmpty();
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    @Transactional
    WorkItem createWorkItem(final String title) {
        final WorkItemCreateRequest req = WorkItemCreateRequest.builder()
                .title(title)
                .category("test")
                .createdBy("test-system")
                .build();
        return workItemService.create(req);
    }

    @Transactional
    WorkItem claimWorkItem(final WorkItem wi, final String assignee) {
        return workItemService.claim(wi.id, assignee);
    }

    @Transactional
    void fireEventInRolledBackTransaction() {
        final WorkItem wi = new WorkItem();
        wi.id = UUID.randomUUID();
        wi.status = WorkItemStatus.PENDING;
        // Fire a synthetic event and then force rollback
        lifecycleEvents.fire(WorkItemLifecycleEvent.of("rollback_marker", wi, "test", null));
        throw new RuntimeException("intentional rollback");
    }
}
