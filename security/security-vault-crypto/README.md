# Vault envelope crypto adapter

This module implements the vault SPI using AES-256-GCM with envelope encryption.

- Every encryption creates a random 256-bit data-encryption key (DEK) and 96-bit nonce.
- The DEK is wrapped by a tenant-scoped `KeyEncryptionPort`; production implementations must delegate to an external KMS/HSM.
- The persisted opaque frame contains the nonce, wrapped DEK, and authenticated ciphertext. It contains no plaintext or unwrapped key.
- AAD binds tenant, vault, secret, version, format version, algorithm, and KEK identifier.
- GCM authentication rejects tampering and cross-scope/version replay.
- The adapter has no built-in key, fallback key, or local-development KEK.

A deployment must provide a configured `KeyEncryptionPort` implementation before enabling vault operations. Missing KMS configuration must fail closed.
