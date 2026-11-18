package tech.kayys.numou.security.vault.spi;

import java.util.UUID;

/**
 * Boundary to a KMS/HSM-backed key-encryption key (KEK).
 * Implementations must resolve keys by tenant and immutable key id, enforce
 * tenant authorization, and never persist plaintext KEKs.
 */
public interface KeyEncryptionPort {
    /** Returns the configured active KEK identifier for this tenant. */
    String activeKeyId(UUID tenantId);

    /** Wraps a 256-bit data-encryption key using the identified KEK. */
    byte[] wrap(UUID tenantId, String keyId, byte[] dataKey);

    /** Unwraps a data-encryption key using the identified KEK. */
    byte[] unwrap(UUID tenantId, String keyId, byte[] wrappedDataKey);
}
