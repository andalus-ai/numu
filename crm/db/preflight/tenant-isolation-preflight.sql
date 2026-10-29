-- Read-only tenant-isolation preflight for PostgreSQL.
-- Run against a restored/staging copy first. This script does not assign tenants,
-- alter application tables, or make any ownership assumptions.
--
-- Usage: psql "$CRM_DATABASE_URL" -v ON_ERROR_STOP=1 -f crm/db/preflight/tenant-isolation-preflight.sql
-- Review every row before introducing tenant_id constraints or backfills.

CREATE TEMP TABLE crm_tenant_preflight (
    table_name text NOT NULL,
    table_exists boolean NOT NULL,
    tenant_id_column_exists boolean NOT NULL,
    row_count bigint,
    assessment text NOT NULL
) ON COMMIT DROP;

DO $preflight$
DECLARE
    target_table text;
    has_table boolean;
    has_tenant_id boolean;
    rows_found bigint;
    targets text[] := ARRAY[
        'crm_accounts',
        'crm_customers',
        'crm_customer_contacts',
        'crm_customer_addresses',
        'crm_leads',
        'crm_lead_activities',
        'crm_opportunities',
        'crm_opportunity_activities',
        'crm_referrals',
        'crm_territories',
        'crm_territory_assignments'
    ];
BEGIN
    FOREACH target_table IN ARRAY targets LOOP
        SELECT EXISTS (
            SELECT 1 FROM information_schema.tables
            WHERE table_schema = current_schema()
              AND table_name = target_table
              AND table_type = 'BASE TABLE'
        ) INTO has_table;

        IF NOT has_table THEN
            INSERT INTO crm_tenant_preflight
                (table_name, table_exists, tenant_id_column_exists, row_count, assessment)
            VALUES (target_table, false, false, NULL, 'TABLE_MISSING: verify migration history and schema');
            CONTINUE;
        END IF;

        SELECT EXISTS (
            SELECT 1 FROM information_schema.columns
            WHERE table_schema = current_schema()
              AND table_name = target_table
              AND column_name = 'tenant_id'
        ) INTO has_tenant_id;

        EXECUTE format('SELECT count(*) FROM %I.%I', current_schema(), target_table)
            INTO rows_found;

        INSERT INTO crm_tenant_preflight
            (table_name, table_exists, tenant_id_column_exists, row_count, assessment)
        VALUES (
            target_table,
            true,
            has_tenant_id,
            rows_found,
            CASE
                WHEN NOT has_tenant_id AND rows_found > 0 THEN 'BLOCK: populated table has no tenant_id; ownership mapping required'
                WHEN NOT has_tenant_id THEN 'BLOCK: tenant_id absent; verify table scope before migration'
                WHEN has_tenant_id AND rows_found > 0 THEN 'REVIEW: inspect null tenant_id and ownership mapping'
                ELSE 'REVIEW: empty table; confirm tenant policy before constraints'
            END
        );
    END LOOP;
END
$preflight$;

SELECT table_name, table_exists, tenant_id_column_exists, row_count, assessment
FROM crm_tenant_preflight
ORDER BY table_name;

-- Explicitly report rows with missing tenant ownership where tenant_id exists.
-- The identifiers are fixed above, so this dynamic query never accepts caller input.
DO $null_check$
DECLARE
    target_table text;
    has_tenant_id boolean;
    null_rows bigint;
    targets text[] := ARRAY[
        'crm_accounts',
        'crm_customers',
        'crm_customer_contacts',
        'crm_customer_addresses',
        'crm_leads',
        'crm_lead_activities',
        'crm_opportunities',
        'crm_opportunity_activities',
        'crm_referrals',
        'crm_territories',
        'crm_territory_assignments'
    ];
BEGIN
    RAISE NOTICE 'Null tenant_id counts (only tables already containing tenant_id):';
    FOREACH target_table IN ARRAY targets LOOP
        IF EXISTS (
            SELECT 1 FROM information_schema.tables
            WHERE table_schema = current_schema()
              AND table_name = target_table
              AND table_type = 'BASE TABLE'
        ) AND EXISTS (
            SELECT 1 FROM information_schema.columns
            WHERE table_schema = current_schema()
              AND table_name = target_table
              AND column_name = 'tenant_id'
        ) THEN
            EXECUTE format('SELECT count(*) FROM %I.%I WHERE tenant_id IS NULL', current_schema(), target_table)
                INTO null_rows;
            RAISE NOTICE '%.tenant_id NULL rows: %', target_table, null_rows;
        END IF;
    END LOOP;
END
$null_check$;
