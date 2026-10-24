CREATE TABLE courier_network_partners (
 tenant_id UUID NOT NULL,
 id UUID NOT NULL,
 partner_code VARCHAR(80) NOT NULL,
 display_name VARCHAR(200) NOT NULL,
 status VARCHAR(24) NOT NULL DEFAULT 'PENDING',
 supported_services JSONB NOT NULL,
 created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
 updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
 PRIMARY KEY (tenant_id,id),
 CONSTRAINT uq_courier_partner_code UNIQUE (tenant_id,partner_code),
 CONSTRAINT ck_courier_partner_status CHECK(status IN ('PENDING','ACTIVE','SUSPENDED','TERMINATED')),
 CONSTRAINT ck_courier_partner_services CHECK(jsonb_typeof(supported_services)='array' AND jsonb_array_length(supported_services)>0)
);
CREATE TABLE courier_partner_shipment_references (
 tenant_id UUID NOT NULL,
 id UUID NOT NULL,
 partner_id UUID NOT NULL,
 shipment_id UUID NOT NULL,
 external_reference VARCHAR(200) NOT NULL,
 linked_at TIMESTAMPTZ NOT NULL DEFAULT now(),
 PRIMARY KEY(tenant_id,id),
 CONSTRAINT fk_courier_partner_ref_partner FOREIGN KEY(tenant_id,partner_id) REFERENCES courier_network_partners(tenant_id,id) ON DELETE RESTRICT,
 CONSTRAINT fk_courier_partner_ref_shipment FOREIGN KEY(tenant_id,shipment_id) REFERENCES courier_shipments(tenant_id,id) ON DELETE RESTRICT,
 CONSTRAINT uq_courier_partner_external_ref UNIQUE(tenant_id,partner_id,external_reference),
 CONSTRAINT uq_courier_partner_shipment UNIQUE(tenant_id,partner_id,shipment_id)
);
CREATE TABLE courier_partner_inbound_events (
 tenant_id UUID NOT NULL,
 id UUID NOT NULL,
 partner_id UUID NOT NULL,
 external_event_id VARCHAR(240) NOT NULL,
 event_type VARCHAR(100) NOT NULL,
 payload JSONB NOT NULL,
 occurred_at TIMESTAMPTZ NOT NULL,
 received_at TIMESTAMPTZ NOT NULL DEFAULT now(),
 processing_status VARCHAR(24) NOT NULL DEFAULT 'RECEIVED',
 processed_at TIMESTAMPTZ,
 attempts INTEGER NOT NULL DEFAULT 0,
 last_error VARCHAR(1000),
 PRIMARY KEY(tenant_id,id),
 CONSTRAINT fk_courier_partner_event_partner FOREIGN KEY(tenant_id,partner_id) REFERENCES courier_network_partners(tenant_id,id) ON DELETE RESTRICT,
 CONSTRAINT uq_courier_partner_event_idempotency UNIQUE(tenant_id,partner_id,external_event_id),
 CONSTRAINT ck_courier_partner_event_status CHECK(processing_status IN ('RECEIVED','PROCESSING','PROCESSED','RETRY','DEAD_LETTER')),
 CONSTRAINT ck_courier_partner_event_attempts CHECK(attempts>=0),
 CONSTRAINT ck_courier_partner_event_processed CHECK(processed_at IS NULL OR processed_at>=received_at)
);
CREATE INDEX ix_courier_partner_event_queue ON courier_partner_inbound_events(tenant_id,received_at,id)
 WHERE processing_status IN ('RECEIVED','RETRY');
CREATE INDEX ix_courier_partner_ref_shipment ON courier_partner_shipment_references(tenant_id,shipment_id);
COMMENT ON TABLE courier_partner_inbound_events IS 'Idempotent inbox for authenticated partner webhooks; process asynchronously and never trust partner payloads as commands without validation.';
