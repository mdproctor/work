package io.casehub.work.api;

public enum ValidationMode {
    /** Reject WorkItem creation if any required capability is not in the registry. */
    STRICT,
    /** Log a warning but proceed. */
    WARN,
    /** No registry check. Default. */
    PERMISSIVE
}
