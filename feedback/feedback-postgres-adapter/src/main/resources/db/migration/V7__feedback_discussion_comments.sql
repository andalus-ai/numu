CREATE TABLE feedback_comments (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL,
    feedback_id UUID NOT NULL,
    parent_comment_id UUID NULL,
    author_type VARCHAR(32) NOT NULL,
    author_id VARCHAR(200) NOT NULL,
    visibility VARCHAR(16) NOT NULL,
    body VARCHAR(20000) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT feedback_comments_visibility_ck CHECK (visibility IN ('PUBLIC', 'INTERNAL')),
    CONSTRAINT feedback_comments_tenant_id_id_uq UNIQUE (tenant_id, id),
    CONSTRAINT feedback_comments_tenant_feedback_id_uq UNIQUE (tenant_id, feedback_id, id),
    CONSTRAINT feedback_comments_tenant_feedback_visibility_uq UNIQUE (tenant_id, feedback_id, id, visibility),
    CONSTRAINT feedback_comments_feedback_fk
        FOREIGN KEY (tenant_id, feedback_id)
        REFERENCES feedback_items (tenant_id, id)
        ON DELETE CASCADE,
    CONSTRAINT feedback_comments_parent_fk
        FOREIGN KEY (tenant_id, feedback_id, parent_comment_id, visibility)
        REFERENCES feedback_comments (tenant_id, feedback_id, id, visibility)
        ON DELETE CASCADE
);

CREATE TABLE feedback_comment_idempotency (
    tenant_id UUID NOT NULL,
    idempotency_key VARCHAR(200) NOT NULL,
    comment_id UUID NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (tenant_id, idempotency_key),
    CONSTRAINT feedback_comment_idempotency_comment_fk
        FOREIGN KEY (tenant_id, comment_id)
        REFERENCES feedback_comments (tenant_id, id)
        ON DELETE CASCADE
);

CREATE INDEX feedback_comments_thread_idx
    ON feedback_comments (tenant_id, feedback_id, visibility, created_at ASC, id ASC);
CREATE INDEX feedback_comments_parent_idx
    ON feedback_comments (tenant_id, feedback_id, parent_comment_id, created_at ASC)
    WHERE parent_comment_id IS NOT NULL;