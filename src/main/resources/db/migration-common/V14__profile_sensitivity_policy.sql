-- Persist the policy applied at collection time so reports and audits can prove how values were
-- protected. Existing rows predate policy tracking and preserve their historical plaintext shape.
ALTER TABLE sql_analyzer.profile_column_stat
    ADD COLUMN IF NOT EXISTS sensitivity_policy VARCHAR(20) NOT NULL DEFAULT 'PLAINTEXT';
