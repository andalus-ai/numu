# Numou Document Storage

`DocumentStoragePort` has a local filesystem implementation for development and
tests and an S3-compatible implementation for production deployments.

The local adapter is disabled in the `prod` Quarkus build profile. Production
must be built with the build-time property
`syirkah.document.storage=s3`; otherwise the application fails closed instead of
silently storing documents on a local disk. Configure the S3 adapter with:

```properties
syirkah.document.s3.bucket=${DOCUMENT_S3_BUCKET}
syirkah.document.s3.region=${DOCUMENT_S3_REGION:us-east-1}
syirkah.document.s3.endpoint=${DOCUMENT_S3_ENDPOINT:}
syirkah.document.s3.path-style=${DOCUMENT_S3_PATH_STYLE:false}
syirkah.document.max-upload-bytes=${DOCUMENT_MAX_UPLOAD_BYTES:104857600}
```

Credentials use the AWS SDK default provider chain. Set an endpoint and
path-style access for compatible services such as MinIO. Upload targets include
the signed `Content-Type` and `If-None-Match` headers; clients must send those
headers as returned. The conditional write prevents a presigned target from
overwriting an object that has already been uploaded. Completion re-reads and
hashes the object before persisting document metadata. This implementation
uses single-part `PutObject`, so configured upload limits cannot exceed 5 GB.

The local adapter returns its one-time credential in the `X-Upload-Token`
header, not in the URL, to avoid leaking it through URL logs and referrers.

The authenticated API creates and completes uploads at:

```text
POST /api/v1/tenants/{tenantId}/documents/upload-sessions
POST /api/v1/tenants/{tenantId}/documents/upload-sessions/{uploadSessionId}/complete
```

Both operations require the identity permission `document.create` for an
active tenant member. The document adapter reuses Identity's principal
resolution and authorization service; applications composing document support
must include the identity adapter and its OIDC configuration.

Document domain events are recorded in the same database transaction as the
document/session writes. A scheduled outbox worker claims messages with
PostgreSQL `FOR UPDATE SKIP LOCKED`, leases them for 30 minutes, and publishes
up to ten at a time to Kafka topic `document.events`. Failed deliveries use capped
exponential retry and move to `DEAD` after the configured attempt limit; the
event ID is included in the envelope for consumer-side deduplication. Delivery
is at-least-once, so consumers must deduplicate by `eventId`.
