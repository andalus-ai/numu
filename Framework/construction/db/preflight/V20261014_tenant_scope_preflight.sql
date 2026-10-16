-- READ-ONLY preflight for V20261014_01__tenant_scope_boq_revision_storage.sql.
-- Run against the target database after V20261009..V20261013 and before V20261014.
-- This report does not backfill, mutate, or authorize the migration.

DO $$
BEGIN
  IF to_regclass('public.construction_boq_revision_snapshot') IS NULL
     OR to_regclass('public.construction_boq_revision_item') IS NULL
     OR to_regclass('public.construction_boq_revision_counter') IS NULL THEN
    RAISE EXCEPTION 'Expected BOQ revision tables are missing in schema public; confirm target schema and migration history before proceeding';
  END IF;
END $$;

SELECT 'construction_boq_revision_snapshot' AS table_name, COUNT(*) AS row_count
FROM public.construction_boq_revision_snapshot
UNION ALL
SELECT 'construction_boq_revision_item', COUNT(*)
FROM public.construction_boq_revision_item
UNION ALL
SELECT 'construction_boq_revision_counter', COUNT(*)
FROM public.construction_boq_revision_counter
ORDER BY table_name;

SELECT
  (SELECT COUNT(*) FROM public.construction_boq_revision_snapshot) = 0
  AND (SELECT COUNT(*) FROM public.construction_boq_revision_item) = 0
  AND (SELECT COUNT(*) FROM public.construction_boq_revision_counter) = 0
  AS empty_tables_meet_migration_guard;

-- IMPORTANT: empty tables satisfy only the migration's mechanical guard.
-- If any rows exist, do not add a default tenant or delete data to force this
-- migration through. Build and review an operator-authored backfill from the
-- authoritative live BOQ -> project -> tenant ownership relation first.
-- The source bundle intentionally does not assume the host application's table names.
