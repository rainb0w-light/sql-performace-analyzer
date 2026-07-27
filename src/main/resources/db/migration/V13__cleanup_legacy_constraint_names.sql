-- PostgreSQL keeps constraint names when V6 renames/moves tables. Normalize those catalog
-- objects in a forward migration so runtime metadata is product-version neutral.
DO $migration$
DECLARE
    item RECORD;
    legacy_prefix TEXT := 'v' || '2_';
    normalized_name TEXT;
BEGIN
    FOR item IN
        SELECT namespace.nspname AS schema_name,
               relation.relname AS table_name,
               constraint_def.conname AS constraint_name
        FROM pg_constraint constraint_def
        JOIN pg_class relation ON relation.oid = constraint_def.conrelid
        JOIN pg_namespace namespace ON namespace.oid = relation.relnamespace
        WHERE namespace.nspname = 'sql_analyzer'
          AND lower(constraint_def.conname) LIKE legacy_prefix || '%'
    LOOP
        normalized_name := substring(item.constraint_name FROM length(legacy_prefix) + 1);
        EXECUTE format('ALTER TABLE %I.%I RENAME CONSTRAINT %I TO %I',
                       item.schema_name, item.table_name, item.constraint_name, normalized_name);
    END LOOP;
END
$migration$;
