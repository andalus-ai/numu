CREATE TABLE courier_rate_cards (
 tenant_id UUID NOT NULL,id UUID NOT NULL,version VARCHAR(64) NOT NULL,service_level VARCHAR(16) NOT NULL,
 origin_zone VARCHAR(80) NOT NULL,destination_zone VARCHAR(80) NOT NULL,currency_code CHAR(3) NOT NULL,
 base_fee NUMERIC(18,2) NOT NULL,per_chargeable_kg NUMERIC(18,4) NOT NULL,dimensional_divisor_cm3_per_kg NUMERIC(18,4) NOT NULL,
 tax_rate NUMERIC(8,6) NOT NULL DEFAULT 0,effective_from TIMESTAMPTZ NOT NULL,effective_until TIMESTAMPTZ,created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
 PRIMARY KEY(tenant_id,id),CONSTRAINT uq_courier_rate_card_version UNIQUE(tenant_id,version),
 CONSTRAINT ck_courier_rate_card_service CHECK(service_level IN ('ECONOMY','STANDARD','EXPRESS','SAME_DAY')),
 CONSTRAINT ck_courier_rate_card_currency CHECK(currency_code ~ '^[A-Z]{3}$'),
 CONSTRAINT ck_courier_rate_card_amounts CHECK(base_fee>=0 AND per_chargeable_kg>=0 AND dimensional_divisor_cm3_per_kg>0 AND tax_rate BETWEEN 0 AND 1),
 CONSTRAINT ck_courier_rate_card_effective CHECK(effective_until IS NULL OR effective_until>effective_from)
);
CREATE INDEX ix_courier_rate_card_lookup ON courier_rate_cards(tenant_id,service_level,origin_zone,destination_zone,effective_from DESC);
CREATE TABLE courier_shipment_quotes (
 tenant_id UUID NOT NULL,id UUID NOT NULL,shipment_id UUID NOT NULL,rate_card_id UUID NOT NULL,rate_card_version VARCHAR(64) NOT NULL,
 currency_code CHAR(3) NOT NULL,actual_weight_kg NUMERIC(12,3) NOT NULL,dimensional_weight_kg NUMERIC(12,3) NOT NULL,
 chargeable_weight_kg NUMERIC(12,3) NOT NULL,base_fee NUMERIC(18,2) NOT NULL,weight_fee NUMERIC(18,2) NOT NULL,
 tax NUMERIC(18,2) NOT NULL,total NUMERIC(18,2) NOT NULL,quoted_at TIMESTAMPTZ NOT NULL,expires_at TIMESTAMPTZ,
 PRIMARY KEY(tenant_id,id),
 CONSTRAINT fk_courier_quote_shipment FOREIGN KEY(tenant_id,shipment_id) REFERENCES courier_shipments(tenant_id,id) ON DELETE RESTRICT,
 CONSTRAINT fk_courier_quote_rate_card FOREIGN KEY(tenant_id,rate_card_id) REFERENCES courier_rate_cards(tenant_id,id) ON DELETE RESTRICT,
 CONSTRAINT ck_courier_quote_weights CHECK(actual_weight_kg>=0 AND dimensional_weight_kg>=0 AND chargeable_weight_kg=GREATEST(actual_weight_kg,dimensional_weight_kg)),
 CONSTRAINT ck_courier_quote_amounts CHECK(base_fee>=0 AND weight_fee>=0 AND tax>=0 AND total=base_fee+weight_fee+tax),
 CONSTRAINT ck_courier_quote_expiry CHECK(expires_at IS NULL OR expires_at>quoted_at)
);
CREATE INDEX ix_courier_quote_shipment_history ON courier_shipment_quotes(tenant_id,shipment_id,quoted_at DESC);
CREATE TABLE courier_cod_obligations (
 tenant_id UUID NOT NULL,id UUID NOT NULL,shipment_id UUID NOT NULL,amount NUMERIC(18,2) NOT NULL,currency_code CHAR(3) NOT NULL,
 status VARCHAR(24) NOT NULL DEFAULT 'DUE',collector_reference VARCHAR(160),collection_branch_code VARCHAR(80),collected_amount NUMERIC(18,2),
 collected_at TIMESTAMPTZ,settlement_reference VARCHAR(160),settled_amount NUMERIC(18,2),variance NUMERIC(18,2),settled_at TIMESTAMPTZ,
 created_at TIMESTAMPTZ NOT NULL DEFAULT now(),updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),PRIMARY KEY(tenant_id,id),
 CONSTRAINT fk_courier_cod_shipment FOREIGN KEY(tenant_id,shipment_id) REFERENCES courier_shipments(tenant_id,id) ON DELETE RESTRICT,
 CONSTRAINT uq_courier_cod_shipment UNIQUE(tenant_id,shipment_id),
 CONSTRAINT ck_courier_cod_amount CHECK(amount>0 AND (collected_amount IS NULL OR collected_amount>=0) AND (settled_amount IS NULL OR settled_amount>=0)),
 CONSTRAINT ck_courier_cod_status CHECK(status IN ('DUE','COLLECTED','SETTLED','VARIANCE_RECORDED')),
 CONSTRAINT ck_courier_cod_currency CHECK(currency_code ~ '^[A-Z]{3}$'),
 CONSTRAINT ck_courier_cod_collection CHECK(status='DUE' OR (collector_reference IS NOT NULL AND collection_branch_code IS NOT NULL AND collected_amount IS NOT NULL AND collected_at IS NOT NULL)),
 CONSTRAINT ck_courier_cod_settlement CHECK(status NOT IN ('SETTLED','VARIANCE_RECORDED') OR (settlement_reference IS NOT NULL AND settled_amount IS NOT NULL AND variance IS NOT NULL AND settled_at IS NOT NULL)),
 CONSTRAINT ck_courier_cod_variance CHECK(status<>'SETTLED' OR variance=0)
);
CREATE INDEX ix_courier_cod_branch_closeout ON courier_cod_obligations(tenant_id,collection_branch_code,status,collected_at);
