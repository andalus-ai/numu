CREATE TABLE leaderboard_score_entry (
    tenant_id          VARCHAR(160) NOT NULL,
    leaderboard_id     UUID NOT NULL,
    entry_id           UUID NOT NULL,
    participant_type   VARCHAR(120) NOT NULL,
    participant_id     VARCHAR(200) NOT NULL,
    entry_kind         VARCHAR(24) NOT NULL,
    score_value        NUMERIC(18, 6) NOT NULL,
    source_type        VARCHAR(160) NOT NULL,
    source_id          VARCHAR(200) NOT NULL,
    idempotency_key    VARCHAR(200) NOT NULL,
    reverses_entry_id  UUID NULL,
    reason             VARCHAR(500) NULL,
    effective_at       TIMESTAMPTZ NOT NULL,
    recorded_at        TIMESTAMPTZ NOT NULL,
    CONSTRAINT pk_leaderboard_score_entry
        PRIMARY KEY (tenant_id, leaderboard_id, entry_id),
    CONSTRAINT uq_leaderboard_score_idempotency
        UNIQUE (tenant_id, leaderboard_id, idempotency_key),
    CONSTRAINT fk_leaderboard_score_reversal
        FOREIGN KEY (tenant_id, leaderboard_id, reverses_entry_id)
        REFERENCES leaderboard_score_entry (tenant_id, leaderboard_id, entry_id),
    CONSTRAINT ck_leaderboard_score_kind
        CHECK (entry_kind IN ('CONTRIBUTION', 'ADJUSTMENT', 'REVERSAL')),
    CONSTRAINT ck_leaderboard_score_nonzero
        CHECK (score_value <> 0),
    CONSTRAINT ck_leaderboard_score_reversal_reference
        CHECK ((entry_kind = 'REVERSAL' AND reverses_entry_id IS NOT NULL)
            OR (entry_kind <> 'REVERSAL' AND reverses_entry_id IS NULL)),
    CONSTRAINT ck_leaderboard_score_adjustment_reason
        CHECK (entry_kind <> 'ADJUSTMENT' OR (reason IS NOT NULL AND length(btrim(reason)) > 0)),
    CONSTRAINT ck_leaderboard_score_time_order
        CHECK (recorded_at >= effective_at)
);

CREATE UNIQUE INDEX uq_leaderboard_score_single_reversal
    ON leaderboard_score_entry (tenant_id, leaderboard_id, reverses_entry_id)
    WHERE entry_kind = 'REVERSAL';

CREATE INDEX ix_leaderboard_score_participant_effective
    ON leaderboard_score_entry
       (tenant_id, leaderboard_id, participant_type, participant_id, effective_at, entry_id);

CREATE OR REPLACE FUNCTION leaderboard_score_validate_reversal()
RETURNS TRIGGER AS $$
DECLARE
    original leaderboard_score_entry%ROWTYPE;
BEGIN
    IF NEW.entry_kind <> 'REVERSAL' THEN
        RETURN NEW;
    END IF;

    SELECT * INTO original
      FROM leaderboard_score_entry
     WHERE tenant_id = NEW.tenant_id
       AND leaderboard_id = NEW.leaderboard_id
       AND entry_id = NEW.reverses_entry_id
     FOR UPDATE;

    IF NOT FOUND THEN
        RAISE EXCEPTION 'reversed score entry does not exist';
    END IF;
    IF original.entry_kind = 'REVERSAL' THEN
        RAISE EXCEPTION 'a reversal cannot itself be reversed';
    END IF;
    IF original.participant_type <> NEW.participant_type
       OR original.participant_id <> NEW.participant_id
       OR original.score_value <> -NEW.score_value THEN
        RAISE EXCEPTION 'reversal must negate the original entry for the same participant';
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_leaderboard_score_validate_reversal
    BEFORE INSERT ON leaderboard_score_entry
    FOR EACH ROW EXECUTE FUNCTION leaderboard_score_validate_reversal();

CREATE OR REPLACE FUNCTION leaderboard_score_reject_mutation()
RETURNS TRIGGER AS $$
BEGIN
    RAISE EXCEPTION 'leaderboard score ledger is append-only; append a correction or reversal';
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_leaderboard_score_append_only
    BEFORE UPDATE OR DELETE ON leaderboard_score_entry
    FOR EACH ROW EXECUTE FUNCTION leaderboard_score_reject_mutation();
