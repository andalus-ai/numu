-- READ-ONLY postflight for V20261014_01__tenant_scope_boq_revision_storage.sql.
-- Run after the migration against the same reviewed target database.
-- This verifies structural invariants only; it does not prove application-level isolation.

DO $$
DECLARE
  table_name TEXT;
  expected_pk TEXT[];
  actual_pk TEXT[];
  target_oid OID;
BEGIN
  FOREACH table_name IN ARRAY ARRAY[
    'construction_boq_revision_snapshot',
    'construction_boq_revision_item',
    'construction_boq_revision_counter'
  ] LOOP
    IF to_regclass(format('public.%I', table_name)) IS NULL THEN
      RAISE EXCEPTION 'Postflight failed: public.% is missing', table_name;
    END IF;
    IF NOT EXISTS (
      SELECT 1
      FROM pg_attribute a
      JOIN pg_class c ON c.oid = a.attrelid
      JOIN pg_namespace n ON n.oid = c.relnamespace
      WHERE n.nspname = 'public'
        AND c.relname = table_name
        AND a.attname = 'tenant_id'
        AND a.attnotnull
        AND NOT a.attisdropped
    ) THEN
      RAISE EXCEPTION 'Postflight failed: public.%.tenant_id is missing or nullable', table_name;
    END IF;
  END LOOP;

  -- Verify ordered composite primary-key columns.
  FOREACH table_name IN ARRAY ARRAY[
    'construction_boq_revision_snapshot',
    'construction_boq_revision_item',
    'construction_boq_revision_counter'
  ] LOOP
    expected_pk := CASE table_name
      WHEN 'construction_boq_revision_snapshot' THEN ARRAY['tenant_id','boq_id','revision_number']
      WHEN 'construction_boq_revision_item' THEN ARRAY['tenant_id','boq_id','revision_number','item_id']
      ELSE ARRAY['tenant_id','boq_id']
    END;
    SELECT c.oid INTO target_oid
    FROM pg_class c JOIN pg_namespace n ON n.oid = c.relnamespace
    WHERE n.nspname = 'public' AND c.relname = table_name;

    SELECT array_agg(a.attname::TEXT ORDER BY k.ordinality)
      INTO actual_pk
    FROM pg_index i
    CROSS JOIN LATERAL unnest(i.indkey) WITH ORDINALITY AS k(attnum, ordinality)
    JOIN pg_attribute a ON a.attrelid = i.indrelid AND a.attnum = k.attnum
    WHERE i.indrelid = target_oid AND i.indisprimary;

    IF actual_pk IS DISTINCT FROM expected_pk THEN
      RAISE EXCEPTION 'Postflight failed: public.% primary key is %, expected %', table_name, actual_pk, expected_pk;
    END IF;
  END LOOP;

  IF NOT EXISTS (
    SELECT 1 FROM pg_constraint c
    JOIN pg_class t ON t.oid = c.conrelid
    JOIN pg_namespace n ON n.oid = t.relnamespace
    WHERE n.nspname = 'public'
      AND t.relname = 'construction_boq_revision_item'
      AND c.conname = 'fk_boq_revision_item_header'
      AND c.contype = 'f'
      AND c.convalidated
      AND pg_get_constraintdef(c.oid) ILIKE '%FOREIGN KEY (tenant_id, boq_id, revision_number)%'
      AND pg_get_constraintdef(c.oid) ILIKE '%REFERENCES construction_boq_revision_snapshot(tenant_id, boq_id, revision_number)%'
  ) THEN
    RAISE EXCEPTION 'Postflight failed: validated tenant-scoped item-to-snapshot foreign key is missing';
  END IF;

  IF NOT EXISTS (
    SELECT 1 FROM pg_class i
    JOIN pg_namespace n ON n.oid = i.relnamespace
    WHERE n.nspname = 'public'
      AND i.relname = 'idx_boq_revision_tenant_project'
      AND pg_get_indexdef(i.oid) ILIKE '%(tenant_id, project_id, boq_id, revision_number DESC)%'
  ) THEN
    RAISE EXCEPTION 'Postflight failed: tenant/project revision lookup index is missing or has unexpected columns';
  END IF;
END $$;

SELECT 'PASS' AS tenant_scope_postflight,
       'tenant_id NOT NULL, ordered composite primary keys, validated composite foreign key, and tenant/project index verified' AS checks;

-- This structural postflight does not validate row ownership, row-level security,
-- application tenant propagation, outbox filtering, or authorization behavior.
