CREATE TABLE courier_pickup_requests (
 tenant_id UUID NOT NULL,id UUID NOT NULL,shipment_id UUID NOT NULL,reference VARCHAR(80) NOT NULL,
 merchant_reference VARCHAR(160) NOT NULL,pickup_address VARCHAR(1000) NOT NULL,
 requested_window_start TIMESTAMPTZ NOT NULL,requested_window_end TIMESTAMPTZ NOT NULL,requested_by VARCHAR(160) NOT NULL,
 status VARCHAR(24) NOT NULL DEFAULT 'REQUESTED',created_at TIMESTAMPTZ NOT NULL,updated_at TIMESTAMPTZ NOT NULL,
 assigned_at TIMESTAMPTZ,collected_at TIMESTAMPTZ,courier_reference VARCHAR(160),
 collection_evidence_reference VARCHAR(512),failure_reason VARCHAR(500),PRIMARY KEY(tenant_id,id),
 CONSTRAINT uq_courier_pickup_reference UNIQUE(tenant_id,reference),
 CONSTRAINT uq_courier_pickup_shipment UNIQUE(tenant_id,shipment_id),
 CONSTRAINT fk_courier_pickup_shipment FOREIGN KEY(tenant_id,shipment_id)
  REFERENCES courier_shipments(tenant_id,id) ON DELETE RESTRICT,
 CONSTRAINT ck_courier_pickup_status CHECK(status IN ('REQUESTED','ASSIGNED','COLLECTED','FAILED','CANCELLED')),
 CONSTRAINT ck_courier_pickup_window CHECK(requested_window_end>requested_window_start),
 CONSTRAINT ck_courier_pickup_timestamps CHECK(updated_at>=created_at
  AND (assigned_at IS NULL OR (assigned_at>=created_at AND assigned_at<=updated_at))
  AND (collected_at IS NULL OR (assigned_at IS NOT NULL AND collected_at>=assigned_at AND collected_at<=updated_at))),
 CONSTRAINT ck_courier_pickup_assignment CHECK(status NOT IN ('ASSIGNED','COLLECTED','FAILED')
  OR (courier_reference IS NOT NULL AND assigned_at IS NOT NULL)),
 CONSTRAINT ck_courier_pickup_collection CHECK(status<>'COLLECTED'
  OR (collected_at IS NOT NULL AND collection_evidence_reference IS NOT NULL)),
 CONSTRAINT ck_courier_pickup_failure CHECK(status<>'FAILED' OR length(trim(failure_reason))>0)
);
CREATE INDEX ix_courier_pickup_dispatch ON courier_pickup_requests(tenant_id,status,requested_window_start,requested_window_end);
CREATE INDEX ix_courier_pickup_merchant_history ON courier_pickup_requests(tenant_id,merchant_reference,created_at DESC);