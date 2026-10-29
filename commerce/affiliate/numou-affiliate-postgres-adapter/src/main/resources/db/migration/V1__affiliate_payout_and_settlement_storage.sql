CREATE TABLE IF NOT EXISTS affiliate_partner_payout (
    payout_id UUID PRIMARY KEY,
    tenant_id VARCHAR(200) NOT NULL,
    partner_id UUID NOT NULL,
    commission_entry_id UUID NOT NULL,
    destination_id VARCHAR(255) NOT NULL,
    currency CHAR(3) NOT NULL,
    amount NUMERIC(38, 18) NOT NULL CHECK (amount > 0),
    idempotency_key VARCHAR(255) NOT NULL,
    correlation_id VARCHAR(255) NOT NULL,
    payout_status VARCHAR(32) NOT NULL CHECK (payout_status IN (
        'REQUESTED', 'SUBMITTED', 'PAID', 'HELD', 'RETRYABLE_FAILURE',
        'RECONCILIATION_REQUIRED', 'RETURNED'
    )),
    attempts INTEGER NOT NULL DEFAULT 0 CHECK (attempts >= 0),
    provider_reference VARCHAR(255),
    last_error VARCHAR(500),
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    version BIGINT NOT NULL DEFAULT 0 CHECK (version >= 0),
    CONSTRAINT uq_affiliate_payout_tenant_idempotency UNIQUE (tenant_id, idempotency_key),
    CONSTRAINT uq_affiliate_payout_tenant_id UNIQUE (tenant_id, payout_id),
    CONSTRAINT ck_affiliate_paid_has_provider_reference CHECK (
        payout_status <> 'PAID' OR provider_reference IS NOT NULL
    ),
    CONSTRAINT ck_affiliate_payout_time_order CHECK (updated_at >= created_at)
);

CREATE INDEX IF NOT EXISTS ix_affiliate_payout_tenant_partner_status
    ON affiliate_partner_payout (tenant_id, partner_id, payout_status, created_at);

CREATE INDEX IF NOT EXISTS ix_affiliate_payout_tenant_commission
    ON affiliate_partner_payout (tenant_id, commission_entry_id);

CREATE TABLE IF NOT EXISTS affiliate_settlement_batch (
    batch_id UUID PRIMARY KEY,
    tenant_id VARCHAR(200) NOT NULL,
    currency CHAR(3) NOT NULL,
    total_amount NUMERIC(38, 18) NOT NULL CHECK (total_amount > 0),
    idempotency_key VARCHAR(255) NOT NULL,
    batch_status VARCHAR(40) NOT NULL CHECK (batch_status IN (
        'READY_FOR_APPROVAL', 'APPROVED', 'SUBMITTING', 'COMPLETED',
        'PARTIALLY_COMPLETED', 'CANCELLED', 'RECONCILIATION_REQUIRED'
    )),
    created_by VARCHAR(255) NOT NULL,
    approved_by VARCHAR(255),
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    version BIGINT NOT NULL DEFAULT 0 CHECK (version >= 0),
    CONSTRAINT uq_affiliate_batch_tenant_idempotency UNIQUE (tenant_id, idempotency_key),
    CONSTRAINT uq_affiliate_batch_tenant_id UNIQUE (tenant_id, batch_id),
    CONSTRAINT ck_affiliate_batch_time_order CHECK (updated_at >= created_at),
    CONSTRAINT ck_affiliate_batch_approval CHECK (
        batch_status NOT IN ('APPROVED', 'SUBMITTING', 'COMPLETED', 'PARTIALLY_COMPLETED')
        OR approved_by IS NOT NULL
    )
);

CREATE INDEX IF NOT EXISTS ix_affiliate_batch_tenant_status
    ON affiliate_settlement_batch (tenant_id, batch_status, created_at);

CREATE TABLE IF NOT EXISTS affiliate_settlement_batch_item (
    tenant_id VARCHAR(200) NOT NULL,
    batch_id UUID NOT NULL,
    payout_id UUID NOT NULL,
    partner_id UUID NOT NULL,
    amount NUMERIC(38, 18) NOT NULL CHECK (amount > 0),
    currency CHAR(3) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (tenant_id, batch_id, payout_id),
    CONSTRAINT fk_affiliate_batch_item_batch FOREIGN KEY (tenant_id, batch_id)
        REFERENCES affiliate_settlement_batch (tenant_id, batch_id) ON DELETE RESTRICT,
    CONSTRAINT fk_affiliate_batch_item_payout FOREIGN KEY (tenant_id, payout_id)
        REFERENCES affiliate_partner_payout (tenant_id, payout_id) ON DELETE RESTRICT
);

-- Active reservations are maintained in the same transaction as batch creation/cancellation.
-- A payout can have at most one active reservation across all concurrent application nodes.
CREATE TABLE IF NOT EXISTS affiliate_settlement_payout_reservation (
    tenant_id VARCHAR(200) NOT NULL,
    payout_id UUID NOT NULL,
    batch_id UUID NOT NULL,
    reserved_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (tenant_id, payout_id),
    CONSTRAINT uq_affiliate_reservation_tenant_batch_payout UNIQUE (tenant_id, batch_id, payout_id),
    CONSTRAINT fk_affiliate_reservation_batch FOREIGN KEY (tenant_id, batch_id)
        REFERENCES affiliate_settlement_batch (tenant_id, batch_id) ON DELETE RESTRICT,
    CONSTRAINT fk_affiliate_reservation_payout FOREIGN KEY (tenant_id, payout_id)
        REFERENCES affiliate_partner_payout (tenant_id, payout_id) ON DELETE RESTRICT
);
