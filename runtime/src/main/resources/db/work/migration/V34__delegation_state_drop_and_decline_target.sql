-- V34: Replace DelegationState column with typed delegation decline target.
-- delegation_state was PENDING/RESOLVED but DelegationState.RESOLVED was never set;
-- WorkItemStatus.DELEGATED now carries the pre-acceptance semantic directly.
ALTER TABLE work_item DROP COLUMN delegation_state;
ALTER TABLE work_item ADD COLUMN delegation_decline_target VARCHAR(10);
