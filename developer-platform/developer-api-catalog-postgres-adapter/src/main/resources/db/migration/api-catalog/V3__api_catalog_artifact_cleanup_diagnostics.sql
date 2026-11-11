-- M5i: retain a bounded, non-sensitive failure code for cleanup operations.
ALTER TABLE developer_api_artifacts
    ADD COLUMN cleanup_last_error_code VARCHAR(120);
