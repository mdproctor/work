package io.casehub.work.api;

/** Identifies which SLA deadline a WorkItem breached. */
public enum BreachType {

    /** Nobody claimed the WorkItem within the pool deadline. */
    CLAIM_EXPIRED,

    /** The WorkItem was claimed but not completed within the completion deadline. */
    COMPLETION_EXPIRED
}
