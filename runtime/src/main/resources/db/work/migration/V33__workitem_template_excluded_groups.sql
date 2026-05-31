-- Refs #184: group-level conflict-of-interest exclusion on WorkItemTemplate
-- Expanded to actor IDs at WorkItem creation time; no excluded_groups column on work_item
ALTER TABLE work_item_template ADD COLUMN excluded_groups TEXT;
