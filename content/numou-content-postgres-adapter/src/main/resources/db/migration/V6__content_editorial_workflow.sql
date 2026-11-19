-- M5: tenant-scoped, revision-bound editorial review persistence.
CREATE TABLE content_editorial_review (
    tenant_id UUID NOT NULL,
    review_id UUID NOT NULL,
    content_id UUID NOT NULL,
    revision_number INTEGER NOT NULL,
    policy_mode VARCHAR(16) NOT NULL,
    required_approvals INTEGER NOT NULL,
    status VARCHAR(24) NOT NULL,
    aggregate_version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    PRIMARY KEY (tenant_id, review_id),
    CONSTRAINT fk_editorial_review_revision
        FOREIGN KEY (tenant_id, content_id, revision_number)
        REFERENCES content_revision (tenant_id, content_id, revision_number)
        ON DELETE RESTRICT,
    CONSTRAINT ck_editorial_review_mode
        CHECK (policy_mode IN ('SINGLE', 'ALL', 'SEQUENTIAL')),
    CONSTRAINT ck_editorial_review_status
        CHECK (status IN ('IN_REVIEW', 'APPROVED', 'CHANGES_REQUESTED', 'CANCELLED')),
    CONSTRAINT ck_editorial_review_approvals
        CHECK (required_approvals >= 1),
    CONSTRAINT ck_editorial_review_version
        CHECK (aggregate_version >= 0)
);

CREATE INDEX ix_editorial_review_content_history
    ON content_editorial_review (tenant_id, content_id, revision_number, created_at DESC);
CREATE UNIQUE INDEX uq_editorial_review_active_revision
    ON content_editorial_review (tenant_id, content_id, revision_number)
    WHERE status = 'IN_REVIEW';

CREATE TABLE content_editorial_reviewer (
    tenant_id UUID NOT NULL,
    review_id UUID NOT NULL,
    reviewer_id UUID NOT NULL,
    reviewer_order INTEGER NOT NULL,
    PRIMARY KEY (tenant_id, review_id, reviewer_id),
    CONSTRAINT uq_editorial_reviewer_order UNIQUE (tenant_id, review_id, reviewer_order),
    CONSTRAINT fk_editorial_reviewer_review
        FOREIGN KEY (tenant_id, review_id)
        REFERENCES content_editorial_review (tenant_id, review_id)
        ON DELETE RESTRICT,
    CONSTRAINT ck_editorial_reviewer_order CHECK (reviewer_order >= 0)
);

CREATE TABLE content_editorial_decision (
    tenant_id UUID NOT NULL,
    review_id UUID NOT NULL,
    reviewer_id UUID NOT NULL,
    decision VARCHAR(24) NOT NULL,
    comment TEXT NOT NULL DEFAULT '',
    decided_at TIMESTAMPTZ NOT NULL,
    PRIMARY KEY (tenant_id, review_id, reviewer_id),
    CONSTRAINT fk_editorial_decision_reviewer
        FOREIGN KEY (tenant_id, review_id, reviewer_id)
        REFERENCES content_editorial_reviewer (tenant_id, review_id, reviewer_id)
        ON DELETE RESTRICT,
    CONSTRAINT ck_editorial_decision_value
        CHECK (decision IN ('APPROVE', 'REQUEST_CHANGES')),
    CONSTRAINT ck_editorial_change_explanation
        CHECK (decision <> 'REQUEST_CHANGES' OR length(btrim(comment)) > 0)
);

CREATE TABLE content_editorial_comment (
    tenant_id UUID NOT NULL,
    review_id UUID NOT NULL,
    comment_id UUID NOT NULL,
    author_id UUID NOT NULL,
    body TEXT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    PRIMARY KEY (tenant_id, review_id, comment_id),
    CONSTRAINT fk_editorial_comment_review
        FOREIGN KEY (tenant_id, review_id)
        REFERENCES content_editorial_review (tenant_id, review_id)
        ON DELETE RESTRICT,
    CONSTRAINT ck_editorial_comment_body CHECK (length(btrim(body)) > 0)
);

CREATE INDEX ix_editorial_comment_review_timeline
    ON content_editorial_comment (tenant_id, review_id, created_at, comment_id);

-- Review decisions and comments are append-only. A later correction requires a new review
-- against a new content revision, rather than rewriting an earlier editorial record.
CREATE OR REPLACE FUNCTION content_reject_editorial_history_mutation()
RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    RAISE EXCEPTION 'editorial decisions and comments are append-only'
        USING ERRCODE = '55000';
END;
$$;

CREATE TRIGGER trg_editorial_decision_immutable
BEFORE UPDATE OR DELETE ON content_editorial_decision
FOR EACH ROW EXECUTE FUNCTION content_reject_editorial_history_mutation();

CREATE TRIGGER trg_editorial_comment_immutable
BEFORE UPDATE OR DELETE ON content_editorial_comment
FOR EACH ROW EXECUTE FUNCTION content_reject_editorial_history_mutation();

CREATE OR REPLACE FUNCTION content_guard_editorial_review_update()
RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    IF NEW.tenant_id IS DISTINCT FROM OLD.tenant_id
       OR NEW.review_id IS DISTINCT FROM OLD.review_id
       OR NEW.content_id IS DISTINCT FROM OLD.content_id
       OR NEW.revision_number IS DISTINCT FROM OLD.revision_number
       OR NEW.policy_mode IS DISTINCT FROM OLD.policy_mode
       OR NEW.required_approvals IS DISTINCT FROM OLD.required_approvals
       OR NEW.created_at IS DISTINCT FROM OLD.created_at THEN
        RAISE EXCEPTION 'editorial review identity and policy snapshot are immutable'
            USING ERRCODE = '55000';
    END IF;

    IF NEW.aggregate_version <> OLD.aggregate_version + 1 THEN
        RAISE EXCEPTION 'editorial review version must advance exactly once'
            USING ERRCODE = '23514';
    END IF;

    IF NEW.status IS DISTINCT FROM OLD.status
       AND NOT (OLD.status = 'IN_REVIEW'
                AND NEW.status IN ('APPROVED', 'CHANGES_REQUESTED', 'CANCELLED')) THEN
        RAISE EXCEPTION 'invalid editorial review transition: % -> %', OLD.status, NEW.status
            USING ERRCODE = '23514';
    END IF;
    RETURN NEW;
END;
$$;

CREATE TRIGGER trg_editorial_review_guard_update
BEFORE UPDATE ON content_editorial_review
FOR EACH ROW EXECUTE FUNCTION content_guard_editorial_review_update();

CREATE OR REPLACE FUNCTION content_guard_editorial_child_insert()
RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE
    review_status VARCHAR(24);
BEGIN
    SELECT status INTO review_status
      FROM content_editorial_review
     WHERE tenant_id = NEW.tenant_id AND review_id = NEW.review_id
     FOR UPDATE;
    IF NOT FOUND OR review_status <> 'IN_REVIEW' THEN
        RAISE EXCEPTION 'editorial history can only be appended while review is active'
            USING ERRCODE = '23514';
    END IF;
    RETURN NEW;
END;
$$;

CREATE TRIGGER trg_editorial_reviewer_insert_guard
BEFORE INSERT ON content_editorial_reviewer
FOR EACH ROW EXECUTE FUNCTION content_guard_editorial_child_insert();

CREATE TRIGGER trg_editorial_decision_insert_guard
BEFORE INSERT ON content_editorial_decision
FOR EACH ROW EXECUTE FUNCTION content_guard_editorial_child_insert();

CREATE TRIGGER trg_editorial_comment_insert_guard
BEFORE INSERT ON content_editorial_comment
FOR EACH ROW EXECUTE FUNCTION content_guard_editorial_child_insert();

-- Durable dispatch intent. A relay may publish these records to Gamelan after commit.
CREATE TABLE content_editorial_outbox (
    tenant_id UUID NOT NULL,
    event_id UUID NOT NULL,
    review_id UUID NOT NULL,
    event_type VARCHAR(48) NOT NULL,
    payload JSONB NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    published_at TIMESTAMPTZ,
    attempts INTEGER NOT NULL DEFAULT 0,
    last_error VARCHAR(2000),
    PRIMARY KEY (tenant_id, event_id),
    CONSTRAINT fk_editorial_outbox_review FOREIGN KEY (tenant_id, review_id)
        REFERENCES content_editorial_review (tenant_id, review_id) ON DELETE RESTRICT,
    CONSTRAINT ck_editorial_outbox_attempts CHECK (attempts >= 0)
);
CREATE INDEX ix_editorial_outbox_pending ON content_editorial_outbox (created_at)
    WHERE published_at IS NULL;

CREATE TRIGGER trg_editorial_reviewer_immutable
BEFORE UPDATE OR DELETE ON content_editorial_reviewer
FOR EACH ROW EXECUTE FUNCTION content_reject_editorial_history_mutation();
