CREATE TABLE courier_eta_estimates (
 tenant_id UUID NOT NULL, id UUID NOT NULL, shipment_id UUID NOT NULL,
 estimated_earliest_at TIMESTAMPTZ NOT NULL, estimated_latest_at TIMESTAMPTZ NOT NULL,
 calculated_at TIMESTAMPTZ NOT NULL, confidence NUMERIC(5,4) NOT NULL,
 model_version VARCHAR(100) NOT NULL, source VARCHAR(24) NOT NULL, explanation_code VARCHAR(100) NOT NULL,
 PRIMARY KEY (tenant_id,id),
 CONSTRAINT fk_courier_eta_shipment FOREIGN KEY (tenant_id,shipment_id)
   REFERENCES courier_shipments(tenant_id,id) ON DELETE RESTRICT,
 CONSTRAINT ck_courier_eta_window CHECK (estimated_latest_at >= estimated_earliest_at AND estimated_latest_at >= calculated_at),
 CONSTRAINT ck_courier_eta_confidence CHECK (confidence BETWEEN 0 AND 1),
 CONSTRAINT ck_courier_eta_source CHECK (source IN ('RULES','HISTORICAL_MODEL','LIVE_TRAFFIC','MANUAL_OVERRIDE'))
);
CREATE INDEX ix_courier_eta_latest ON courier_eta_estimates (tenant_id,shipment_id,calculated_at DESC);

CREATE TABLE courier_route_plans (
 tenant_id UUID NOT NULL, id UUID NOT NULL, branch_code VARCHAR(80) NOT NULL, courier_reference VARCHAR(160) NOT NULL,
 service_date DATE NOT NULL, version INTEGER NOT NULL, status VARCHAR(16) NOT NULL DEFAULT 'DRAFT',
 planned_at TIMESTAMPTZ NOT NULL, planned_distance_meters BIGINT, planned_duration_seconds INTEGER,
 PRIMARY KEY (tenant_id,id), CONSTRAINT uq_courier_route_version UNIQUE (tenant_id,branch_code,courier_reference,service_date,version),
 CONSTRAINT ck_courier_route_version CHECK (version > 0),
 CONSTRAINT ck_courier_route_status CHECK (status IN ('DRAFT','OPTIMIZED','DISPATCHED','COMPLETED','CANCELLED')),
 CONSTRAINT ck_courier_route_metrics CHECK ((planned_distance_meters IS NULL OR planned_distance_meters >= 0)
   AND (planned_duration_seconds IS NULL OR planned_duration_seconds >= 0))
);
CREATE INDEX ix_courier_route_dispatch ON courier_route_plans (tenant_id,branch_code,service_date,status);

CREATE TABLE courier_route_plan_stops (
 tenant_id UUID NOT NULL, route_plan_id UUID NOT NULL, sequence INTEGER NOT NULL,
 stop_reference VARCHAR(200) NOT NULL, stop_type VARCHAR(24) NOT NULL, planned_arrival_at TIMESTAMPTZ NOT NULL,
 PRIMARY KEY (tenant_id,route_plan_id,sequence),
 CONSTRAINT fk_courier_route_stop_plan FOREIGN KEY (tenant_id,route_plan_id)
   REFERENCES courier_route_plans(tenant_id,id) ON DELETE CASCADE,
 CONSTRAINT uq_courier_route_stop_ref UNIQUE (tenant_id,route_plan_id,stop_reference),
 CONSTRAINT ck_courier_route_stop_sequence CHECK (sequence > 0),
 CONSTRAINT ck_courier_route_stop_type CHECK (stop_type IN ('PICKUP','DELIVERY','HUB_TRANSFER','BREAK','RETURN_TO_DEPOT'))
);
