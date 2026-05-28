-- Named outcomes per WorkItemTemplate — Refs #169
--
-- WorkItemTemplate gains a declared outcome list (JSON array of {name, displayName}).
-- WorkItem gains three fields snapshotted at instantiation time:
--   template_id       — provenance: which template created this item
--   permitted_outcomes — JSON array of name strings copied from the template at instantiation
--   outcome           — the name recorded when the item is completed

ALTER TABLE work_item_template
    ADD COLUMN outcomes TEXT;

ALTER TABLE work_item ADD COLUMN template_id UUID;
ALTER TABLE work_item ADD COLUMN permitted_outcomes TEXT;
ALTER TABLE work_item ADD COLUMN outcome VARCHAR(255);
