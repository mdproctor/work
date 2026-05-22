-- V30: add last_accessed to routing_cursor for TTL-based GC
-- Existing rows get now() as a safe default (no cursor is truly stale on upgrade).
ALTER TABLE routing_cursor
    ADD COLUMN last_accessed TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now();
