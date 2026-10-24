-- M17: make the database the final departure-readiness gate. Application checks alone
-- are insufficient because a separate writer could mark a wave DEPARTED concurrently.
-- Until an audited resolution workflow exists, every discrepancy is blocking.
CREATE OR REPLACE FUNCTION courier_guard_dispatch_wave_departure()
RETURNS TRIGGER
LANGUAGE plpgsql
AS $$
BEGIN
    IF NEW.status = 'DEPARTED' AND OLD.status IS DISTINCT FROM 'DEPARTED' THEN
        IF EXISTS (
            SELECT 1
            FROM courier_dispatch_wave_parcels p
            WHERE p.tenant_id = NEW.tenant_id
              AND p.wave_id = NEW.id
              AND (
                  (p.expected = TRUE AND p.scan_count = 0)
                  OR p.discrepancy IS NOT NULL
              )
        ) THEN
            RAISE EXCEPTION 'dispatch wave % has missing or discrepant parcel scans', NEW.reference
                USING ERRCODE = 'check_violation',
                      CONSTRAINT = 'ck_courier_dispatch_wave_departure_ready';
        END IF;

        IF EXISTS (
            SELECT 1
            FROM courier_dispatch_wave_discrepancies d
            WHERE d.tenant_id = NEW.tenant_id
              AND d.wave_id = NEW.id
        ) THEN
            RAISE EXCEPTION 'dispatch wave % has unresolved load discrepancies', NEW.reference
                USING ERRCODE = 'check_violation',
                      CONSTRAINT = 'ck_courier_dispatch_wave_departure_ready';
        END IF;
    END IF;
    RETURN NEW;
END;
$$;

DROP TRIGGER IF EXISTS trg_courier_dispatch_wave_departure_guard ON courier_dispatch_waves;
CREATE TRIGGER trg_courier_dispatch_wave_departure_guard
BEFORE UPDATE OF status ON courier_dispatch_waves
FOR EACH ROW
EXECUTE FUNCTION courier_guard_dispatch_wave_departure();

-- Index supports the trigger's tenant-scoped readiness query. The parcel table's
-- primary key already covers (tenant_id, wave_id); this partial index narrows scans
-- to rows that can block departure.
CREATE INDEX IF NOT EXISTS ix_courier_dispatch_wave_blocking_parcels
    ON courier_dispatch_wave_parcels (tenant_id, wave_id)
    WHERE (expected = TRUE AND scan_count = 0) OR discrepancy IS NOT NULL;
