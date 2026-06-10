-- Creates a restricted app_user role for RLS enforcement tests.
-- Superusers bypass RLS unconditionally (GE-20260603-1559a3).
-- This role has DML but no SUPERUSER — RLS policies apply.
CREATE ROLE app_user LOGIN PASSWORD 'app_password' NOSUPERUSER;
GRANT ALL ON SCHEMA public TO app_user;
GRANT ALL ON ALL TABLES IN SCHEMA public TO app_user;
GRANT ALL ON ALL SEQUENCES IN SCHEMA public TO app_user;
ALTER DEFAULT PRIVILEGES IN SCHEMA public GRANT ALL ON TABLES TO app_user;
ALTER DEFAULT PRIVILEGES IN SCHEMA public GRANT ALL ON SEQUENCES TO app_user;
