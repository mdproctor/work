-- Refs #170: snapshotted schema fields on WorkItem
ALTER TABLE work_item ADD COLUMN input_data_schema TEXT;
ALTER TABLE work_item ADD COLUMN output_data_schema TEXT;
