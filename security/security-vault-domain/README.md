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

## Workload identity value

`VaultWorkloadIdentity` is a validated, tenant-bound result of a trusted identity-resolution step. It is not a credential and cannot authenticate a caller by itself. Its issuer, subject, audience and scope claims are bounded; expiry is checked against the application clock before policy evaluation. Only the host identity adapter may construct it from an already authenticated platform principal.


## Vault policy evaluation

The domain provides immutable VaultPolicyRule records, explicit ALLOW/DENY effects, stable subject namespaces (principal ID, role, and verified workload subject), and a deterministic default-deny evaluator.

- Tenant is always exact; vault and secret may be exact or explicitly wildcarded.
- Operation is always exact and maps to the canonical least-privilege scope.
- Rule validity is inclusive at validFrom and exclusive at validUntil.
- Any applicable explicit deny takes precedence over every allow, regardless of rule version.
- Workload-subject rules bind the exact subject, trusted issuer, audience, tenant, expiry and operation scope.
- The result contains only decision metadata and a matched rule ID; it never contains secret material.

The evaluator is a domain primitive, not a persisted policy service. A host adapter must load a consistent policy snapshot, authenticate workload identities before creating their value objects, persist policy changes with version control, and durably audit decisions. Missing policy data must produce default deny; adapters must not convert storage or evaluation errors into allow decisions.
