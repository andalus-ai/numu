-- Immutable tenant-scoped survey template versions and bearer-token distribution grants.
CREATE TABLE feedback_survey_templates (
    tenant_id UUID NOT NULL,
    template_code VARCHAR(100) NOT NULL,
    template_version INTEGER NOT NULL CHECK (template_version > 0),
    title VARCHAR(200) NOT NULL,
    description VARCHAR(2000) NOT NULL DEFAULT '',
    survey_definition TEXT NOT NULL CHECK (jsonb_typeof(survey_definition::jsonb) IS NOT NULL),
    definition_sha256 CHAR(64) NOT NULL,
    status VARCHAR(16) NOT NULL CHECK (status IN ('DRAFT', 'PUBLISHED', 'ARCHIVED')),
    created_at TIMESTAMPTZ NOT NULL,
    published_at TIMESTAMPTZ NULL,
    PRIMARY KEY (tenant_id, template_code, template_version),
    CHECK ((status = 'PUBLISHED' AND published_at IS NOT NULL)
        OR (status <> 'PUBLISHED' AND published_at IS NULL))
);

CREATE INDEX feedback_survey_templates_latest_published_idx
    ON feedback_survey_templates (tenant_id, template_code, template_version DESC)
    WHERE status = 'PUBLISHED';

CREATE TABLE feedback_survey_distributions (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL,
    survey_id VARCHAR(200) NOT NULL,
    channel VARCHAR(24) NOT NULL CHECK (channel IN ('PUBLIC_LINK', 'QR_CODE', 'EMAIL', 'NOTIFICATION')),
    token_hash CHAR(64) NOT NULL UNIQUE,
    expires_at TIMESTAMPTZ NOT NULL,
    max_responses INTEGER NOT NULL CHECK (max_responses BETWEEN 1 AND 1000000),
    accepted_responses INTEGER NOT NULL DEFAULT 0 CHECK (accepted_responses >= 0),
    reserved_responses INTEGER NOT NULL DEFAULT 0 CHECK (reserved_responses >= 0),
    status VARCHAR(16) NOT NULL CHECK (status IN ('ACTIVE', 'REVOKED', 'EXPIRED')),
    created_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT feedback_survey_distribution_tenant_id_uq UNIQUE (tenant_id, id),
    CHECK (expires_at > created_at),
    CHECK (accepted_responses + reserved_responses <= max_responses)
);

CREATE INDEX feedback_survey_distributions_survey_idx
    ON feedback_survey_distributions (tenant_id, survey_id, created_at DESC);
CREATE INDEX feedback_survey_distributions_active_expiry_idx
    ON feedback_survey_distributions (expires_at)
    WHERE status = 'ACTIVE';

CREATE TABLE feedback_survey_distribution_response_claims (
    tenant_id UUID NOT NULL,
    distribution_id UUID NOT NULL,
    response_id VARCHAR(200) NOT NULL,
    claim_status VARCHAR(16) NOT NULL CHECK (claim_status IN ('RESERVED', 'COMPLETED', 'RELEASED')),
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    PRIMARY KEY (tenant_id, distribution_id, response_id),
    CONSTRAINT feedback_distribution_claim_fk FOREIGN KEY (tenant_id, distribution_id)
        REFERENCES feedback_survey_distributions (tenant_id, id) ON DELETE CASCADE
);
CREATE INDEX feedback_distribution_claims_status_idx
    ON feedback_survey_distribution_response_claims (tenant_id, distribution_id, claim_status);
