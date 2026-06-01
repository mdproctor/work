package io.casehub.work.api;

import java.util.UUID;

/**
 * Utilities for parsing the {@code WorkItem.callerRef} field.
 *
 * <p>The engine sets {@code callerRef} to {@code "{caseId}:{planItemId}"} when spawning
 * WorkItems from a case binding. Externally created WorkItems may use any format —
 * parsing returns {@code null} rather than throwing when the format is unrecognised.
 */
public final class WorkItemCallerRef {

    private WorkItemCallerRef() {}

    /**
     * Parses the case UUID from a callerRef string.
     *
     * @param callerRef the WorkItem callerRef; may be null
     * @return the case UUID, or null if callerRef is null, has no colon,
     *         or the first segment is not a valid UUID
     */
    public static UUID parseCaseId(final String callerRef) {
        if (callerRef == null || !callerRef.contains(":")) return null;
        try {
            return UUID.fromString(callerRef.split(":")[0]);
        } catch (final IllegalArgumentException e) {
            return null;
        }
    }
}
