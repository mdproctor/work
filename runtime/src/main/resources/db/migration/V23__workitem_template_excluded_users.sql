-- Refs #171: excluded users for conflict-of-interest enforcement on WorkItemTemplate
ALTER TABLE work_item_template ADD COLUMN excluded_users TEXT;
