package io.casehub.work.api;

/** Lifecycle events that trigger worker (re-)selection. */
public enum AssignmentTrigger {
    /** WorkItem first created and persisted. */
    CREATED,
    /** WorkItem returned to pool by its assignee (release). */
    RELEASED,
    /** WorkItem delegated to a different pool or individual. */
    DELEGATED,
    /** WorkItem returned to an escalation pool after an SLA breach. */
    SLA_ESCALATED,
    /** WorkItem returned to pool after a delegatee declined a targeted delegation. */
    DELEGATION_DECLINED
}
