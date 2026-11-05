CREATE TABLE advertising_campaign_placement (
    tenant_id VARCHAR(200) NOT NULL,
    advertiser_account_id UUID NOT NULL,
    campaign_id UUID NOT NULL,
    placement_id UUID NOT NULL,
    assigned_at TIMESTAMPTZ NOT NULL,
    PRIMARY KEY (tenant_id,campaign_id,placement_id),
    CONSTRAINT fk_advertising_campaign_placement_campaign
      FOREIGN KEY (tenant_id,campaign_id,advertiser_account_id)
      REFERENCES advertising_campaign (tenant_id,campaign_id,advertiser_account_id) ON DELETE RESTRICT,
    CONSTRAINT fk_advertising_campaign_placement_placement
      FOREIGN KEY (tenant_id,placement_id)
      REFERENCES advertising_ad_placement (tenant_id,placement_id) ON DELETE RESTRICT
);
CREATE INDEX ix_advertising_campaign_placement_by_inventory
    ON advertising_campaign_placement (tenant_id,placement_id,campaign_id);
CREATE INDEX ix_advertising_campaign_delivery
    ON advertising_campaign (tenant_id,starts_at,ends_at,campaign_id)
    WHERE campaign_status='ACTIVE';
CREATE INDEX ix_advertising_creative_delivery
    ON advertising_creative (tenant_id,creative_format,creative_id)
    WHERE creative_status='APPROVED';

ALTER TABLE advertising_ad_placement
  ADD CONSTRAINT uq_advertising_placement_property UNIQUE (tenant_id,placement_id,property_id);

CREATE TABLE advertising_ad_decision (
    tenant_id VARCHAR(200) NOT NULL,
    decision_id UUID NOT NULL,
    placement_id UUID NULL,
    property_id UUID NULL,
    campaign_id UUID NULL,
    advertiser_account_id UUID NULL,
    creative_id UUID NULL,
    decision_status VARCHAR(16) NOT NULL CHECK (decision_status IN ('FILLED','NO_FILL')),
    decision_reason VARCHAR(40) NOT NULL CHECK (decision_reason IN (
      'SELECTED','PLACEMENT_NOT_FOUND','INVENTORY_NOT_ELIGIBLE',
      'PROPERTY_ORIGIN_MISMATCH','FORMAT_MISMATCH','NO_ELIGIBLE_CANDIDATE'
    )),
    decided_at TIMESTAMPTZ NOT NULL,
    PRIMARY KEY (tenant_id,decision_id),
    -- placement_id is retained as the requested opaque identifier even for unknown-placement no-fill decisions.
    CONSTRAINT fk_advertising_decision_property
      FOREIGN KEY (tenant_id,property_id)
      REFERENCES advertising_publisher_property (tenant_id,property_id) ON DELETE RESTRICT,
    CONSTRAINT fk_advertising_decision_placement_property
      FOREIGN KEY (tenant_id,placement_id,property_id)
      REFERENCES advertising_ad_placement (tenant_id,placement_id,property_id) ON DELETE RESTRICT,
    CONSTRAINT fk_advertising_decision_campaign_owner
      FOREIGN KEY (tenant_id,campaign_id,advertiser_account_id)
      REFERENCES advertising_campaign (tenant_id,campaign_id,advertiser_account_id) ON DELETE RESTRICT,
    CONSTRAINT fk_advertising_decision_creative_owner
      FOREIGN KEY (tenant_id,creative_id,advertiser_account_id)
      REFERENCES advertising_creative (tenant_id,creative_id,advertiser_account_id) ON DELETE RESTRICT,
    CONSTRAINT fk_advertising_decision_campaign_placement
      FOREIGN KEY (tenant_id,campaign_id,placement_id)
      REFERENCES advertising_campaign_placement (tenant_id,campaign_id,placement_id) ON DELETE RESTRICT,
    CONSTRAINT fk_advertising_decision_campaign_creative
      FOREIGN KEY (tenant_id,campaign_id,creative_id)
      REFERENCES advertising_campaign_creative (tenant_id,campaign_id,creative_id) ON DELETE RESTRICT,
    CONSTRAINT ck_advertising_decision_outcome CHECK (
      (decision_status='FILLED' AND placement_id IS NOT NULL AND property_id IS NOT NULL
        AND campaign_id IS NOT NULL AND advertiser_account_id IS NOT NULL AND creative_id IS NOT NULL AND decision_reason='SELECTED')
      OR
      (decision_status='NO_FILL' AND campaign_id IS NULL AND advertiser_account_id IS NULL AND creative_id IS NULL AND decision_reason<>'SELECTED')
    )
);
CREATE INDEX ix_advertising_decision_tenant_placement_time
    ON advertising_ad_decision (tenant_id,placement_id,decided_at DESC);
CREATE INDEX ix_advertising_decision_tenant_campaign_time
    ON advertising_ad_decision (tenant_id,campaign_id,decided_at DESC)
    WHERE campaign_id IS NOT NULL;
