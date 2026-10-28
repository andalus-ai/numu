-- M53: durable tenant-scoped idempotency for shipment booking retries.
-- Inserted in the same transaction as the shipment and parcel writes.
CREATE TABLE courier_shipment_booking_keys (
    tenant_id UUID NOT NULL,
    idempotency_key VARCHAR(200) NOT NULL,
    request_fingerprint CHAR(64) NOT NULL,
    shipment_id UUID NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (tenant_id, idempotency_key),
    CONSTRAINT ck_courier_booking_idempotency_key CHECK (length(trim(idempotency_key)) BETWEEN 1 AND 200),
    CONSTRAINT ck_courier_booking_fingerprint CHECK (request_fingerprint ~ '^[0-9a-f]{64}$')
);
CREATE INDEX ix_courier_booking_idempotency_shipment ON courier_shipment_booking_keys (tenant_id, shipment_id);
