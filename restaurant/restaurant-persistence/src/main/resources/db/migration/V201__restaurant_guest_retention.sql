-- Guest personal-data retention (Indonesia PDP Law / GDPR storage limitation).
-- Reservations and waitlist entries keep a guest's name, phone and email only while they are operationally needed.
-- This function irreversibly anonymises documents whose visit/queue time is older than the given age.
--
-- Run it from a scheduled maintenance job using a dedicated role that has BYPASSRLS (it spans all tenants);
-- the application role must NOT have that attribute. Example:
--     SELECT restaurant_anonymize_guests(interval '180 days');
-- Returns the number of rows changed. Idempotent: already-anonymised rows are skipped.

CREATE OR REPLACE FUNCTION restaurant_anonymize_guests(older_than interval) RETURNS bigint AS $$
DECLARE
    changed bigint;
BEGIN
    UPDATE restaurant_document
    SET doc = jsonb_set(jsonb_set(jsonb_set(doc, '{guestName}', '"[anonymised]"'),
                                  '{guestPhone}', 'null'), '{guestEmail}', 'null'),
        updated_at = now()
    WHERE kind IN ('reservation', 'waitlist')
      AND starts_at < now() - older_than
      AND doc->>'guestName' IS DISTINCT FROM '[anonymised]'
      AND status IN ('COMPLETED', 'CANCELLED', 'NO_SHOW', 'SEATED', 'LEFT', 'EXPIRED');
    GET DIAGNOSTICS changed = ROW_COUNT;
    RETURN changed;
END;
$$ LANGUAGE plpgsql;
