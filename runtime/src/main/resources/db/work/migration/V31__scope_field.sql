-- V31: add scope field to work_item and work_item_template (Refs work#212, work#213)
-- Nullable — null means unscoped (expiry service uses Path.root() as fallback).
-- Propagated from template at instantiation; set directly on creation for engine-created items (engine#330).
ALTER TABLE work_item ADD COLUMN scope VARCHAR(255);
ALTER TABLE work_item_template ADD COLUMN scope VARCHAR(255);
