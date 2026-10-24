-- Partner agreements and outbound integration metadata.
-- courier_network_partners is created by V9__courier_network_partners_and_inbound_events.sql.
CREATE TABLE courier_partner_agreements (
 tenant_id UUID NOT NULL, id UUID NOT NULL, partner_id UUID NOT NULL, version VARCHAR(64) NOT NULL,
 service_level VARCHAR(16) NOT NULL, origin_zone VARCHAR(80) NOT NULL, destination_zone VARCHAR(80) NOT NULL,
 effective_from TIMESTAMPTZ NOT NULL, effective_until TIMESTAMPTZ, status VARCHAR(16) NOT NULL DEFAULT 'DRAFT',
 created_at TIMESTAMPTZ NOT NULL DEFAULT now(), updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
 PRIMARY KEY (tenant_id,id),
 CONSTRAINT fk_courier_agreement_partner FOREIGN KEY (tenant_id,partner_id)
   REFERENCES courier_network_partners(tenant_id,id) ON DELETE RESTRICT,
 CONSTRAINT uq_courier_agreement_version UNIQUE (tenant_id,partner_id,version),
 CONSTRAINT ck_courier_agreement_service CHECK (service_level IN ('ECONOMY','STANDARD','EXPRESS','SAME_DAY')),
 CONSTRAINT ck_courier_agreement_status CHECK (status IN ('DRAFT','ACTIVE','SUSPENDED','EXPIRED')),
 CONSTRAINT ck_courier_agreement_window CHECK (effective_until IS NULL OR effective_until > effective_from),
 CONSTRAINT ck_courier_agreement_timestamps CHECK (updated_at >= created_at)
);
CREATE INDEX ix_courier_partner_agreement_lane ON courier_partner_agreements
 (tenant_id,service_level,origin_zone,destination_zone,status,effective_from);

CREATE TABLE courier_integration_endpoints (
 tenant_id UUID NOT NULL, id UUID NOT NULL, code VARCHAR(80) NOT NULL, target_uri TEXT NOT NULL,
 secret_reference VARCHAR(500) NOT NULL, event_types TEXT[] NOT NULL, signature_mode VARCHAR(24) NOT NULL,
 status VARCHAR(16) NOT NULL DEFAULT 'DISABLED',
 created_at TIMESTAMPTZ NOT NULL DEFAULT now(), updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
 PRIMARY KEY (tenant_id,id), CONSTRAINT uq_courier_integration_code UNIQUE (tenant_id,code),
 CONSTRAINT ck_courier_integration_https CHECK (target_uri ~ '^https://[^[:space:]]+$'),
 CONSTRAINT ck_courier_integration_secret_ref CHECK (length(trim(secret_reference)) > 0),
 CONSTRAINT ck_courier_integration_events CHECK (cardinality(event_types) > 0),
 CONSTRAINT ck_courier_integration_signature CHECK (signature_mode IN ('HMAC_SHA256','MUTUAL_TLS','NONE')),
 CONSTRAINT ck_courier_integration_status CHECK (status IN ('DISABLED','ACTIVE','SUSPENDED')),
 CONSTRAINT ck_courier_integration_timestamps CHECK (updated_at >= created_at)
);
CREATE INDEX ix_courier_integration_dispatch ON courier_integration_endpoints (tenant_id,status);
