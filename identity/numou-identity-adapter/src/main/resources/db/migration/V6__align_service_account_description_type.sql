-- Keep the persisted service-account description type aligned with the JPA String mapping.
-- V2 originally created this column as TEXT, while Hibernate maps an unannotated String to
-- VARCHAR(255). Fail safely rather than truncate any existing descriptions during migration.
DO $$
BEGIN
    IF EXISTS (
        SELECT 1
          FROM service_accounts
         WHERE length(description) > 255
    ) THEN
        RAISE EXCEPTION 'Cannot narrow service_accounts.description to VARCHAR(255): rows exceed 255 characters; shorten or migrate those descriptions explicitly first';
    END IF;
END $$;

ALTER TABLE service_accounts
    ALTER COLUMN description TYPE VARCHAR(255);
