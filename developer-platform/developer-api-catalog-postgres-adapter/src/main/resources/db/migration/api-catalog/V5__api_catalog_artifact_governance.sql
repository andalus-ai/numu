-- M5k: explicit legal-hold and retention governance with attributable immutable history.
ALTER TABLE developer_api_artifact_cleanup_audit
    DROP CONSTRAINT developer_api_artifact_cleanup_audit_action_check;

ALTER TABLE developer_api_artifact_cleanup_audit
    ADD CONSTRAINT ck_developer_api_artifact_cleanup_audit_action
    CHECK (action IN (
        'CLEANUP_CLAIMED', 'CLEANUP_DELETED', 'RETRY_SCHEDULED', 'TERMINAL_FAILURE',
        'LEGAL_HOLD_APPLIED', 'LEGAL_HOLD_RELEASED', 'RETENTION_EXTENDED'
    ));

ALTER TABLE developer_api_artifact_cleanup_audit
    ADD COLUMN legal_hold_state BOOLEAN,
    ADD COLUMN retention_until TIMESTAMPTZ,
    ADD COLUMN reason TEXT CHECK (reason IS NULL OR length(reason) <= 500);
