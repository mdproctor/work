package io.casehub.work.runtime.event;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;

import org.junit.jupiter.api.Test;

import io.casehub.work.runtime.model.WorkItem;
import io.casehub.work.runtime.model.WorkItemStatus;

/**
 * Verifies that WorkItemLifecycleEvent carries the outcome field. Refs #169.
 */
class WorkItemLifecycleEventOutcomeTest {

    private WorkItem workItem(final UUID id, final WorkItemStatus status, final String outcome) {
        final WorkItem wi = new WorkItem();
        wi.id = id;
        wi.status = status;
        wi.outcome = outcome;
        return wi;
    }

    @Test
    void of_completedWithOutcome_carriesOutcome() {
        final WorkItem wi = workItem(UUID.randomUUID(), WorkItemStatus.COMPLETED, "approved");

        final WorkItemLifecycleEvent event = WorkItemLifecycleEvent.of("COMPLETED", wi, "alice", null);

        assertThat(event.outcome()).isEqualTo("approved");
    }

    @Test
    void of_withNullOutcome_outcomeIsNull() {
        final WorkItem wi = workItem(UUID.randomUUID(), WorkItemStatus.COMPLETED, null);

        final WorkItemLifecycleEvent event = WorkItemLifecycleEvent.of("COMPLETED", wi, "alice", null);

        assertThat(event.outcome()).isNull();
    }

    @Test
    void fromWire_preservesOutcome() {
        final UUID id = UUID.randomUUID();

        final WorkItemLifecycleEvent event = WorkItemLifecycleEvent.fromWire(
                "io.casehub.work.workitem.completed",
                "/workitems/" + id,
                id.toString(),
                id, WorkItemStatus.COMPLETED,
                java.time.Instant.now(),
                "alice", null, null, null,
                "approved");

        assertThat(event.outcome()).isEqualTo("approved");
    }
}
