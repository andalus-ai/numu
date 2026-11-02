# Numou Security Vault — Domain

This module defines provider-neutral identifiers and the immutable encrypted-secret record.

## Security invariants

- A secret's logical identity is stable; each write creates a new positive version.
- A persisted record contains ciphertext and key metadata only, never plaintext.
- Ciphertext is defensively copied on ingress and egress.
- Diagnostic rendering redacts ciphertext.
- Tenant ID is part of every encrypted record's identity and must participate in repository uniqueness and encryption associated data.
- Expiry must be later than creation time.

The domain intentionally does not implement cryptography, persistence, HTTP, cloud KMS, or provider SDK integrations. Those are explicit ports and adapters.

## Threat-model boundary

This module cannot itself prevent a privileged process from reading plaintext while it is in memory. Callers and adapters must minimize plaintext lifetime, avoid immutable String conversion, clear mutable buffers where practical, and never log secret values. Production deployments must protect process memory, backups, key material, and operator access as separate controls.
