-- V7__processed_command.sql
SET search_path TO accounting, public;

CREATE TABLE IF NOT EXISTS processed_command (
    request_id VARCHAR(128) PRIMARY KEY,
    command_type VARCHAR(256) NOT NULL,
    processed_at TIMESTAMP WITH TIME ZONE NOT NULL,
    result_token VARCHAR(256)
);

CREATE INDEX IF NOT EXISTS idx_processed_command_processed_at ON processed_command (processed_at);
