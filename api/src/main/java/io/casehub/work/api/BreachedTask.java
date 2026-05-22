package io.casehub.work.api;

import java.util.Set;
import java.util.UUID;

/**
 * Projection of a WorkItem that has breached its SLA deadline.
 * Passed to {@link SlaBreachPolicy#onBreach} so the policy can make
 * routing decisions without depending on the full WorkItem entity.
 */
public record BreachedTask(

        UUID taskId,

        /**
         * Opaque caller reference echoed from WorkItem — format is {@code case:{id}/pi:{id}}
         * for engine-created tasks; null for directly created tasks.
         */
        String callerRef,

        String title,

        /** Comma-separated candidateGroups split into a set; empty if none assigned. */
        Set<String> candidateGroups) {
}
