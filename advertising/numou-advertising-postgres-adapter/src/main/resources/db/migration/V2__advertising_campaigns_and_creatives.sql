CREATE TABLE advertising_campaign (
    tenant_id VARCHAR(200) NOT NULL,
    campaign_id UUID NOT NULL,
    advertiser_account_id UUID NOT NULL,
    campaign_name VARCHAR(200) NOT NULL,
    objective VARCHAR(32) NOT NULL CHECK (objective IN ('AWARENESS','TRAFFIC','LEADS','SALES','APP_INSTALLS')),
    starts_at TIMESTAMPTZ NOT NULL,
    ends_at TIMESTAMPTZ NULL,
    campaign_status VARCHAR(24) NOT NULL CHECK (campaign_status IN ('DRAFT','SCHEDULED','ACTIVE','PAUSED','COMPLETED','ARCHIVED')),
    created_at TIMESTAMPTZ NOT NULL,
    version BIGINT NOT NULL DEFAULT 0 CHECK (version >= 0),
    PRIMARY KEY (tenant_id, campaign_id),
    CONSTRAINT uq_advertising_campaign_owner UNIQUE (tenant_id,campaign_id,advertiser_account_id),
    CONSTRAINT fk_advertising_campaign_advertiser
      FOREIGN KEY (tenant_id, advertiser_account_id)
      REFERENCES advertising_advertiser_account (tenant_id, account_id) ON DELETE RESTRICT,
    CONSTRAINT ck_advertising_campaign_window CHECK (ends_at IS NULL OR ends_at > starts_at)
);
CREATE INDEX ix_advertising_campaign_tenant_advertiser_status
    ON advertising_campaign (tenant_id, advertiser_account_id, campaign_status, starts_at);

CREATE TABLE advertising_creative (
    tenant_id VARCHAR(200) NOT NULL,
    creative_id UUID NOT NULL,
    advertiser_account_id UUID NOT NULL,
    creative_name VARCHAR(200) NOT NULL,
    creative_format VARCHAR(16) NOT NULL CHECK (creative_format IN ('TEXT','IMAGE','VIDEO','NATIVE')),
    headline VARCHAR(200) NULL,
    body VARCHAR(2000) NULL,
    destination_uri VARCHAR(2048) NULL,
    document_reference VARCHAR(512) NULL,
    creative_status VARCHAR(24) NOT NULL CHECK (creative_status IN ('DRAFT','SUBMITTED','APPROVED','REJECTED','ARCHIVED')),
    review_note VARCHAR(1000) NULL,
    created_at TIMESTAMPTZ NOT NULL,
    version BIGINT NOT NULL DEFAULT 0 CHECK (version >= 0),
    PRIMARY KEY (tenant_id, creative_id),
    CONSTRAINT uq_advertising_creative_owner UNIQUE (tenant_id,creative_id,advertiser_account_id),
    CONSTRAINT fk_advertising_creative_advertiser
      FOREIGN KEY (tenant_id, advertiser_account_id)
      REFERENCES advertising_advertiser_account (tenant_id, account_id) ON DELETE RESTRICT,
    CONSTRAINT ck_advertising_creative_asset CHECK (creative_format = 'TEXT' OR document_reference IS NOT NULL)
);
CREATE INDEX ix_advertising_creative_tenant_advertiser_status
    ON advertising_creative (tenant_id, advertiser_account_id, creative_status, created_at);

CREATE TABLE advertising_campaign_creative (
    tenant_id VARCHAR(200) NOT NULL,
    advertiser_account_id UUID NOT NULL,
    campaign_id UUID NOT NULL,
    creative_id UUID NOT NULL,
    assigned_at TIMESTAMPTZ NOT NULL,
    PRIMARY KEY (tenant_id, campaign_id, creative_id),
    CONSTRAINT fk_advertising_campaign_creative_campaign
      FOREIGN KEY (tenant_id,campaign_id,advertiser_account_id)
      REFERENCES advertising_campaign (tenant_id,campaign_id,advertiser_account_id) ON DELETE RESTRICT,
    CONSTRAINT fk_advertising_campaign_creative_creative
      FOREIGN KEY (tenant_id,creative_id,advertiser_account_id)
      REFERENCES advertising_creative (tenant_id,creative_id,advertiser_account_id) ON DELETE RESTRICT
);
CREATE INDEX ix_advertising_campaign_creative_by_creative
    ON advertising_campaign_creative (tenant_id, creative_id, campaign_id);
