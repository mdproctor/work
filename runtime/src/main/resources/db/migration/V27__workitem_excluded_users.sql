-- Refs #171: excluded users snapshotted onto WorkItem at instantiation
ALTER TABLE work_item ADD COLUMN excluded_users TEXT;
