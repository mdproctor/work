-- V29: routing_cursor — persistent round-robin cursor per candidate pool
-- pool_hash: SHA-256 (64 hex chars) of sorted candidate IDs
-- last_index: last assigned index; starts at -1 so first call returns index 0
-- version: OCC column
CREATE TABLE routing_cursor (
    pool_hash  VARCHAR(64)  NOT NULL,
    last_index INTEGER      NOT NULL DEFAULT -1,
    version    INTEGER      NOT NULL DEFAULT 0,
    CONSTRAINT pk_routing_cursor PRIMARY KEY (pool_hash)
);
