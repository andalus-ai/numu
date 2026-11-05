# Organization invitation delivery outbox

Organization owns invitation delivery intent because it owns the invitation lifecycle and recipient authorization. This is distinct from Notification's registered-device push queue.

The outbox stores the recipient address and an authenticated-encryption envelope for the one-time bearer secret. Plaintext bearer material is not persisted in the queue. The `VaultInvitationSecretCipherAdapter` bridges to Numou Vault's `SecretEncryptionPort` contract; the host must supply an envelope implementation backed by a managed KMS/HSM `KeyEncryptionPort`. Tenant and invitation identity are bound into the encryption scope. Production delivery must not be enabled until key provisioning, rotation, recovery, and access controls are configured.

`JdbcOrganizationInvitationRepository.persist` inserts the invitation and encrypted delivery intent in one transaction. The queue adapter claims batches with PostgreSQL `FOR UPDATE SKIP LOCKED` and assigns fresh lease tokens. Sent, retry, and dead-letter transitions are fenced by tenant and lease token; expired leases can be reclaimed. The scheduler invokes `OrganizationInvitationDeliveryWorker.runBatch`; the worker starts no threads. Retries are capped at eight attempts with capped exponential backoff. Only normalized error codes are persisted.

`InvitationEmailProvider` is a provider-neutral email boundary. A deployment supplies an implementation with bounded network timeouts and an HTTPS public invitation URL. No SMTP, SES, SendGrid, or other vendor adapter is selected in this milestone.

## Composition and remaining work

- Use the delivery-enabled `OrganizationInvitationService` constructor and call `issueAndQueue`; this returns invitation metadata, not the bearer secret.
- The legacy `issue` and `resend` methods still return the one-time secret to a trusted host integration for source compatibility. They must not be exposed directly by an untrusted API.
- Secure resend is implemented by `OrganizationInvitationService.resendAndQueue`: it rotates the invitation digest, supersedes older pending or leased sends, and appends a new encrypted delivery in one transaction. Migration V6 permits multiple delivery attempts per invitation.
- Runtime/API wiring, provider implementation, host scheduler, key provisioning, and an operational dead-letter replay interface remain composition/release work.
- The V5 migration must run after V4 through the deployment's migration runner; the Organization adapter does not itself run migrations.


Before sending, the worker reloads the invitation under its tenant scope and verifies that it is still pending, unexpired, and that the decrypted secret digest matches the invitation's current digest. Stale, expired, or superseded queue entries are dead-lettered without calling the email provider. Provider requests carry the delivery ID as an idempotency key; adapters should honor it to reduce duplicate sends after lease expiry or ambiguous network outcomes.
