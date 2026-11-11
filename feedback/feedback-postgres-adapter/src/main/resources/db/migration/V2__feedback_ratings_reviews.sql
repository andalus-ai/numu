CREATE TABLE feedback_ratings (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL,
    target_type VARCHAR(64) NOT NULL,
    target_id VARCHAR(200) NOT NULL,
    branch_id VARCHAR(200) NULL,
    metric_code VARCHAR(32) NOT NULL,
    scale_min NUMERIC(16,4) NOT NULL,
    scale_max NUMERIC(16,4) NOT NULL,
    rating_value NUMERIC(16,4) NOT NULL,
    respondent_type VARCHAR(32) NULL,
    respondent_id VARCHAR(200) NULL,
    survey_reference VARCHAR(200) NULL,
    submitted_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT feedback_ratings_scale_ck CHECK (scale_min < scale_max),
    CONSTRAINT feedback_ratings_value_ck CHECK (rating_value >= scale_min AND rating_value <= scale_max),
    CONSTRAINT feedback_ratings_tenant_id_id_uq UNIQUE (tenant_id, id)
);

CREATE TABLE feedback_rating_idempotency (
    tenant_id UUID NOT NULL,
    idempotency_key VARCHAR(200) NOT NULL,
    rating_id UUID NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (tenant_id, idempotency_key),
    CONSTRAINT feedback_rating_idempotency_fk
        FOREIGN KEY (tenant_id, rating_id)
        REFERENCES feedback_ratings (tenant_id, id)
        ON DELETE CASCADE
);

CREATE TABLE feedback_reviews (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL,
    target_type VARCHAR(64) NOT NULL,
    target_id VARCHAR(200) NOT NULL,
    branch_id VARCHAR(200) NULL,
    author_type VARCHAR(32) NOT NULL,
    author_id VARCHAR(200) NOT NULL,
    rating_id UUID NOT NULL,
    title VARCHAR(200) NOT NULL,
    body VARCHAR(20000) NOT NULL,
    status VARCHAR(20) NOT NULL,
    verification_status VARCHAR(20) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    moderated_at TIMESTAMPTZ NULL,
    CONSTRAINT feedback_reviews_tenant_id_id_uq UNIQUE (tenant_id, id),
    CONSTRAINT feedback_reviews_rating_once_uq UNIQUE (tenant_id, rating_id),
    CONSTRAINT feedback_reviews_rating_fk
        FOREIGN KEY (tenant_id, rating_id)
        REFERENCES feedback_ratings (tenant_id, id)
);

CREATE INDEX feedback_ratings_target_metric_idx
    ON feedback_ratings (tenant_id, target_type, target_id, metric_code, submitted_at DESC);
CREATE INDEX feedback_ratings_branch_metric_idx
    ON feedback_ratings (tenant_id, branch_id, metric_code, submitted_at DESC)
    WHERE branch_id IS NOT NULL;
CREATE INDEX feedback_reviews_public_idx
    ON feedback_reviews (tenant_id, target_type, target_id, created_at DESC)
    WHERE status = 'PUBLISHED';
CREATE INDEX feedback_reviews_moderation_idx
    ON feedback_reviews (tenant_id, status, created_at);
