-- Replace the non-unique name index with a UNIQUE constraint.
-- Enforces name uniqueness at the DB level; the application-level ambiguity guard in
-- WorkItemTemplateService.findByName is now a dead code path.
DROP INDEX IF EXISTS idx_work_item_template_name;
ALTER TABLE work_item_template ADD CONSTRAINT uq_work_item_template_name UNIQUE (name);
