-- Versioned envelope encryption for provider credentials.
-- Existing rows remain marked as legacy plaintext and are deliberately rejected by the new adapter
-- until the device owner re-registers or an approved offline migration re-encrypts them.
ALTER TABLE notification_push_device
    ADD COLUMN provider_token_ciphertext TEXT NULL,
    ADD COLUMN provider_token_key_id VARCHAR(120) NULL;

ALTER TABLE notification_push_device
    ALTER COLUMN provider_token DROP NOT NULL;

ALTER TABLE notification_push_device
    ADD CONSTRAINT notification_push_device_token_storage_shape
    CHECK (
        (provider_token IS NOT NULL AND provider_token_ciphertext IS NULL AND provider_token_key_id IS NULL)
        OR
        (provider_token IS NULL AND provider_token_ciphertext IS NOT NULL AND provider_token_key_id IS NOT NULL)
    );
