# Numou Documents / Storage

This is a standalone Maven reactor for provider-neutral binary storage. It is deliberately separate from Workspace and project aggregates: those capabilities keep document references and evidence links; this capability owns binary-object persistence adapters.

## Modules

- `numou-document-storage-spi`: tenant-scoped storage contract, key validation and provider-neutral metadata.
- `numou-document-storage-local`: local filesystem adapter with content-length and SHA-256 validation, atomic replacement where supported, and symlink-path checks.
- `numou-document-storage-s3`: S3 API adapter for AWS S3 and compatible endpoints such as RustFS.
- `numou-document-storage-config`: explicit storage mode/settings and adapter factory.

## Select a mode

- `LOCAL`: store on a configured local filesystem path.
- `CLOUD`: use AWS S3 with the SDK default credential provider chain unless explicit credentials are supplied.
- `EXTERNAL`: use an S3-compatible endpoint. The provider defaults to `rustfs`, the development endpoint defaults to `http://localhost:9000`, and path-style access is normally enabled for RustFS.

Example configuration contract for host applications (map these values into `DocumentStorageSettings`; this library does not auto-read environment variables):

```yaml
numou:
  document:
    storage:
      mode: external
      provider: rustfs
      local:
        root-path: ${NUMOU_DOCUMENT_PATH:./data/documents}
      cloud:
        region: ${AWS_REGION:us-east-1}
        bucket: ${NUMOU_DOCUMENT_BUCKET:}
      external:
        endpoint: ${RUSTFS_ENDPOINT:http://localhost:9000}
        region: ${RUSTFS_REGION:us-east-1}
        bucket: ${RUSTFS_BUCKET:numou-documents}
        path-style-access: true
        access-key: ${RUSTFS_ACCESS_KEY:}
        secret-key: ${RUSTFS_SECRET_KEY:}
```

This YAML is an integration contract, not a framework-specific configuration binding. A host runtime must bind it and compose the factory explicitly.

## Example

```java
DocumentStorageSettings settings = DocumentStorageSettings.externalRustFs(
    URI.create(System.getenv().getOrDefault("RUSTFS_ENDPOINT", "http://localhost:9000")),
    System.getenv().getOrDefault("RUSTFS_BUCKET", "numou-documents"),
    System.getenv("RUSTFS_ACCESS_KEY"),
    System.getenv("RUSTFS_SECRET_KEY"),
    Boolean.parseBoolean(System.getenv().getOrDefault("NUMOU_PRODUCTION", "false")));

try (DocumentStorage storage = DocumentStorageProviderFactory.create(settings)) {
    StorageKey key = new StorageKey(tenantId, documentObjectId);
    StoredObjectMetadata metadata = storage.put(key, uploadStream, contentLength, contentType, expectedSha256);
}
```

## Production requirements and current limits

- Use HTTPS for external endpoints in production. Do not expose RustFS directly to the public internet without TLS and an appropriate network/security boundary.
- Inject credentials through a secrets manager or environment; never commit secrets. The SDK default credential chain is used when explicit access/secret keys are omitted.
- Provision the bucket and its policies out of band; the adapter does not create buckets or grant access.
- The storage key's tenant prefix is a namespace boundary, **not authorization**. Callers must authorize tenant/document access before calling this SPI.
- Configure tenant-specific IAM/bucket policy where applicable, private networking, encryption at rest, backups/replication, lifecycle policies, request timeouts and metrics in the host runtime.
- Local filesystem deployments must use a dedicated application-owned directory and OS-level permissions. Symlink checks reduce path-escape risk but do not replace filesystem isolation and permission hardening.
- The S3 adapter persists a supplied SHA-256 value as object metadata; it does not independently recompute the checksum during the remote upload. Compute/verify it in the upload application before calling the adapter.
- Versioned-object semantics, presigned URLs, malware scanning, content-type sniffing, retention/legal hold, and per-object authorization are not implemented in this initial adapter layer. Add these behind explicit contracts before relying on them for regulated document workflows.

## Build

Run from this directory because the repository has multiple Maven reactor roots:

```bash
mvn test
```
