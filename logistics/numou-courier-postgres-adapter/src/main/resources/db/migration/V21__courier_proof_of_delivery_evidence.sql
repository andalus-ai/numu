-- M19: immutable, tenant-scoped proof-of-delivery evidence.
CREATE TABLE courier_proof_of_delivery (
    tenant_id UUID NOT NULL,
    id UUID NOT NULL,
    shipment_reference VARCHAR(160) NOT NULL,
    delivery_attempt_reference VARCHAR(160) NOT NULL,
    delivered_at TIMESTAMPTZ NOT NULL,
    recipient_label VARCHAR(160) NOT NULL,
    evidence_reference VARCHAR(500) NOT NULL,
    evidence_sha256 CHAR(64) NOT NULL,
    actor_reference VARCHAR(160) NOT NULL,
    source VARCHAR(64) NOT NULL,
    idempotency_key VARCHAR(160) NOT NULL,
    payload_fingerprint VARCHAR(128) NOT NULL,
    occurred_at TIMESTAMPTZ NOT NULL,
    recorded_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (tenant_id, id),
    CONSTRAINT uq_courier_pod_idempotency UNIQUE (tenant_id, source, idempotency_key),
    CONSTRAINT ck_courier_pod_shipment CHECK (length(trim(shipment_reference)) BETWEEN 1 AND 160),
    CONSTRAINT ck_courier_pod_attempt CHECK (length(trim(delivery_attempt_reference)) BETWEEN 1 AND 160),
    CONSTRAINT ck_courier_pod_recipient CHECK (length(trim(recipient_label)) BETWEEN 1 AND 160),
    CONSTRAINT ck_courier_pod_evidence_ref CHECK (length(trim(evidence_reference)) BETWEEN 1 AND 500),
    CONSTRAINT ck_courier_pod_digest CHECK (evidence_sha256 ~ '^[0-9a-fA-F]{64}$'),
    CONSTRAINT ck_courier_pod_actor CHECK (length(trim(actor_reference)) BETWEEN 1 AND 160),
    CONSTRAINT ck_courier_pod_source CHECK (length(trim(source)) BETWEEN 1 AND 64),
    CONSTRAINT ck_courier_pod_idempotency CHECK (length(trim(idempotency_key)) BETWEEN 1 AND 160),
    CONSTRAINT ck_courier_pod_fingerprint CHECK (length(trim(payload_fingerprint)) BETWEEN 1 AND 128),
    CONSTRAINT ck_courier_pod_time CHECK (delivered_at <= occurred_at AND recorded_at >= occurred_at)
);

CREATE INDEX ix_courier_pod_shipment_timeline
    ON courier_proof_of_delivery (tenant_id, shipment_reference, delivered_at DESC);

CREATE OR REPLACE FUNCTION courier_reject_pod_mutation()
RETURNS TRIGGER
LANGUAGE plpgsql
AS $$
BEGIN
    RAISE EXCEPTION 'proof-of-delivery evidence is append-only'
        USING ERRCODE = 'check_violation',
              CONSTRAINT = 'ck_courier_pod_append_only';
END;
$$;

CREATE TRIGGER trg_courier_pod_immutable
BEFORE UPDATE OR DELETE ON courier_proof_of_delivery
FOR EACH ROW
EXECUTE FUNCTION courier_reject_pod_mutation();
