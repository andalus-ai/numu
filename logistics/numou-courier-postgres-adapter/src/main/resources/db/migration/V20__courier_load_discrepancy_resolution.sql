-- M18: record explicit, append-only resolutions for load discrepancies.
-- Departure remains fail-closed unless every durable discrepancy is resolved and
-- missing expected parcels have an explicit waiver/removal resolution.
CREATE TABLE courier_dispatch_wave_discrepancy_resolutions (
    tenant_id UUID NOT NULL,
    id UUID NOT NULL,
    wave_id UUID NOT NULL,
    discrepancy_id UUID,
    parcel_reference VARCHAR(160) NOT NULL,
    resolution_code VARCHAR(32) NOT NULL,
    reason VARCHAR(500) NOT NULL,
    actor_reference VARCHAR(160) NOT NULL,
    permission_reference VARCHAR(200) NOT NULL,
    evidence_reference VARCHAR(500),
    source VARCHAR(64) NOT NULL,
    idempotency_key VARCHAR(160) NOT NULL,
    occurred_at TIMESTAMPTZ NOT NULL,
    recorded_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (tenant_id, id),
    CONSTRAINT fk_courier_wave_resolution_wave
        FOREIGN KEY (tenant_id, wave_id)
        REFERENCES courier_dispatch_waves (tenant_id, id) ON DELETE RESTRICT,
    CONSTRAINT fk_courier_wave_resolution_discrepancy
        FOREIGN KEY (tenant_id, discrepancy_id)
        REFERENCES courier_dispatch_wave_discrepancies (tenant_id, id) ON DELETE RESTRICT,
    CONSTRAINT uq_courier_wave_resolution_idempotency
        UNIQUE (tenant_id, source, idempotency_key),
    CONSTRAINT uq_courier_wave_resolution_discrepancy
        UNIQUE (tenant_id, discrepancy_id),
    CONSTRAINT ck_courier_wave_resolution_code CHECK (
        resolution_code IN ('ACCEPT_UNEXPECTED', 'ACKNOWLEDGE_DUPLICATE',
                            'WAIVE_MISSING', 'REMOVE_FROM_MANIFEST')
    ),
    CONSTRAINT ck_courier_wave_resolution_reason CHECK (length(trim(reason)) BETWEEN 1 AND 500),
    CONSTRAINT ck_courier_wave_resolution_actor CHECK (length(trim(actor_reference)) BETWEEN 1 AND 160),
    CONSTRAINT ck_courier_wave_resolution_permission CHECK (length(trim(permission_reference)) BETWEEN 1 AND 200),
    CONSTRAINT ck_courier_wave_resolution_source CHECK (length(trim(source)) BETWEEN 1 AND 64),
    CONSTRAINT ck_courier_wave_resolution_idempotency CHECK (length(trim(idempotency_key)) BETWEEN 1 AND 160),
    CONSTRAINT ck_courier_wave_resolution_parcel CHECK (length(trim(parcel_reference)) BETWEEN 1 AND 160),
    CONSTRAINT ck_courier_wave_resolution_time CHECK (recorded_at >= occurred_at),
    CONSTRAINT ck_courier_wave_resolution_discrepancy_ref CHECK (
        (discrepancy_id IS NOT NULL AND resolution_code IN ('ACCEPT_UNEXPECTED','ACKNOWLEDGE_DUPLICATE','WAIVE_MISSING','REMOVE_FROM_MANIFEST'))
        OR (discrepancy_id IS NULL AND resolution_code IN ('WAIVE_MISSING','REMOVE_FROM_MANIFEST'))
    )
);

CREATE INDEX ix_courier_wave_resolution_timeline
    ON courier_dispatch_wave_discrepancy_resolutions (tenant_id, wave_id, recorded_at DESC);

-- Serialize resolution with departure by locking the same tenant-owned wave row.
-- The application must obtain permission_reference from its trusted authorization
-- decision and must not accept it as caller-supplied proof.
CREATE OR REPLACE FUNCTION courier_validate_wave_discrepancy_resolution()
RETURNS TRIGGER
LANGUAGE plpgsql
AS $$
DECLARE
    wave_status VARCHAR(24);
    discrepancy_wave UUID;
    discrepancy_parcel VARCHAR(160);
    discrepancy_kind VARCHAR(24);
BEGIN
    SELECT status INTO wave_status
    FROM courier_dispatch_waves
    WHERE tenant_id = NEW.tenant_id AND id = NEW.wave_id
    FOR UPDATE;

    IF NOT FOUND THEN
        RAISE EXCEPTION 'dispatch wave not found in tenant scope'
            USING ERRCODE = 'foreign_key_violation';
    END IF;
    IF wave_status <> 'LOADING' THEN
        RAISE EXCEPTION 'discrepancy resolutions are only allowed while a wave is LOADING'
            USING ERRCODE = 'check_violation',
                  CONSTRAINT = 'ck_courier_wave_resolution_loading';
    END IF;

    IF NEW.discrepancy_id IS NOT NULL THEN
        SELECT wave_id, parcel_reference, discrepancy_type
          INTO discrepancy_wave, discrepancy_parcel, discrepancy_kind
        FROM courier_dispatch_wave_discrepancies
        WHERE tenant_id = NEW.tenant_id AND id = NEW.discrepancy_id
        FOR SHARE;

        IF NOT FOUND OR discrepancy_wave <> NEW.wave_id OR discrepancy_parcel <> NEW.parcel_reference THEN
            RAISE EXCEPTION 'resolution does not match the tenant, wave, and parcel discrepancy'
                USING ERRCODE = 'check_violation',
                      CONSTRAINT = 'ck_courier_wave_resolution_scope';
        END IF;

        IF NOT (
            (discrepancy_kind = 'UNEXPECTED_PARCEL' AND NEW.resolution_code = 'ACCEPT_UNEXPECTED')
            OR (discrepancy_kind = 'DUPLICATE_SCAN' AND NEW.resolution_code = 'ACKNOWLEDGE_DUPLICATE')
            OR (discrepancy_kind = 'MISSING_PARCEL' AND NEW.resolution_code IN ('WAIVE_MISSING','REMOVE_FROM_MANIFEST'))
        ) THEN
            RAISE EXCEPTION 'resolution code is incompatible with discrepancy type'
                USING ERRCODE = 'check_violation',
                      CONSTRAINT = 'ck_courier_wave_resolution_kind';
        END IF;
    ELSE
        IF NOT EXISTS (
            SELECT 1 FROM courier_dispatch_wave_parcels p
            WHERE p.tenant_id = NEW.tenant_id
              AND p.wave_id = NEW.wave_id
              AND p.parcel_reference = NEW.parcel_reference
              AND p.expected = TRUE
              AND p.scan_count = 0
        ) THEN
            RAISE EXCEPTION 'a resolution without discrepancy_id must target a missing expected parcel'
                USING ERRCODE = 'check_violation',
                      CONSTRAINT = 'ck_courier_wave_resolution_missing_parcel';
        END IF;
    END IF;

    RETURN NEW;
END;
$$;

CREATE TRIGGER trg_courier_wave_discrepancy_resolution_validate
BEFORE INSERT ON courier_dispatch_wave_discrepancy_resolutions
FOR EACH ROW
EXECUTE FUNCTION courier_validate_wave_discrepancy_resolution();

-- Resolutions are audit evidence. Correct an erroneous decision by adding a
-- compensating domain workflow in a future migration; never rewrite history.
CREATE OR REPLACE FUNCTION courier_reject_wave_discrepancy_resolution_mutation()
RETURNS TRIGGER
LANGUAGE plpgsql
AS $$
BEGIN
    RAISE EXCEPTION 'dispatch-wave discrepancy resolutions are append-only'
        USING ERRCODE = 'check_violation',
              CONSTRAINT = 'ck_courier_wave_resolution_append_only';
END;
$$;

CREATE TRIGGER trg_courier_wave_discrepancy_resolution_immutable
BEFORE UPDATE OR DELETE ON courier_dispatch_wave_discrepancy_resolutions
FOR EACH ROW
EXECUTE FUNCTION courier_reject_wave_discrepancy_resolution_mutation();

-- Replace M17's fail-closed guard with a resolution-aware guard. No free-form
-- authorized_exception_reason can bypass these checks.
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
              AND p.expected = TRUE
              AND p.scan_count = 0
              AND NOT EXISTS (
                  SELECT 1
                  FROM courier_dispatch_wave_discrepancy_resolutions r
                  WHERE r.tenant_id = p.tenant_id
                    AND r.wave_id = p.wave_id
                    AND r.parcel_reference = p.parcel_reference
                    AND r.resolution_code IN ('WAIVE_MISSING','REMOVE_FROM_MANIFEST')
              )
        ) THEN
            RAISE EXCEPTION 'dispatch wave % has missing expected parcels without an explicit resolution', NEW.reference
                USING ERRCODE = 'check_violation',
                      CONSTRAINT = 'ck_courier_dispatch_wave_departure_ready';
        END IF;

        IF EXISTS (
            SELECT 1
            FROM courier_dispatch_wave_parcels p
            WHERE p.tenant_id = NEW.tenant_id
              AND p.wave_id = NEW.id
              AND p.discrepancy IS NOT NULL
              AND (
                  NOT EXISTS (
                      SELECT 1 FROM courier_dispatch_wave_discrepancies d
                      WHERE d.tenant_id = p.tenant_id
                        AND d.wave_id = p.wave_id
                        AND d.parcel_reference = p.parcel_reference
                  )
                  OR EXISTS (
                      SELECT 1 FROM courier_dispatch_wave_discrepancies d
                      WHERE d.tenant_id = p.tenant_id
                        AND d.wave_id = p.wave_id
                        AND d.parcel_reference = p.parcel_reference
                        AND NOT EXISTS (
                            SELECT 1 FROM courier_dispatch_wave_discrepancy_resolutions r
                            WHERE r.tenant_id = d.tenant_id
                              AND r.discrepancy_id = d.id
                        )
                  )
              )
        ) THEN
            RAISE EXCEPTION 'dispatch wave % has unresolved parcel discrepancy markers', NEW.reference
                USING ERRCODE = 'check_violation',
                      CONSTRAINT = 'ck_courier_dispatch_wave_departure_ready';
        END IF;

        IF EXISTS (
            SELECT 1
            FROM courier_dispatch_wave_discrepancies d
            WHERE d.tenant_id = NEW.tenant_id
              AND d.wave_id = NEW.id
              AND NOT EXISTS (
                  SELECT 1 FROM courier_dispatch_wave_discrepancy_resolutions r
                  WHERE r.tenant_id = d.tenant_id
                    AND r.discrepancy_id = d.id
              )
        ) THEN
            RAISE EXCEPTION 'dispatch wave % has unresolved load discrepancies', NEW.reference
                USING ERRCODE = 'check_violation',
                      CONSTRAINT = 'ck_courier_dispatch_wave_departure_ready';
        END IF;
    END IF;
    RETURN NEW;
END;
$$;

CREATE INDEX IF NOT EXISTS ix_courier_wave_resolution_by_parcel
    ON courier_dispatch_wave_discrepancy_resolutions (tenant_id, wave_id, parcel_reference, resolution_code);
