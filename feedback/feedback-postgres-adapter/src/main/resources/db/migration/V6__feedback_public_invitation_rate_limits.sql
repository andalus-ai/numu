-- Shared, atomic per-invitation request windows for anonymous respondent endpoints.
-- Raw bearer tokens and client addresses are never stored in this table.
CREATE TABLE feedback_public_invitation_rate_limits (
    token_hash CHAR(64) NOT NULL,
    window_start TIMESTAMPTZ NOT NULL,
    request_count INTEGER NOT NULL CHECK (request_count > 0),
    PRIMARY KEY (token_hash, window_start)
);

CREATE INDEX feedback_public_invitation_rate_limits_expiry_idx
    ON feedback_public_invitation_rate_limits (window_start);