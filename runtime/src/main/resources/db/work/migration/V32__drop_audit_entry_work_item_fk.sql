-- Allow CREATE_DENIED audit entries for WorkItems that were never persisted.
-- BlockedAttemptAuditService pre-generates a WorkItem ID before the exclusion check
-- so CREATE_DENIED can reference it; the WorkItem itself is never inserted.
-- Without this, the FK constraint prevents storing orphaned audit entries.
ALTER TABLE audit_entry DROP CONSTRAINT fk_audit_entry_work_item;
