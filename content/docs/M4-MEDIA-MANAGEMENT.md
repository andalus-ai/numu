# M4 — Media Management and Document Integration

## Ownership boundaries
- CMS owns media-library metadata, classification, captions, alt text, technical media metadata and references to exact content revisions.
- Document owns binary document identity, immutable versions, upload sessions and version-scoped malware scan results.
- The separate Documents storage reactor owns provider-neutral binary storage adapters (local and S3-compatible/RustFS). The current Document application upload flow is the integration boundary used by this CMS milestone; do not create another CMS blob store.
- The Fixed Asset capability is not a CMS media library.

## Implemented in M4
- Media kinds: IMAGE, VIDEO, AUDIO and DOCUMENT.
- Media records refer to an exact Document ID and immutable Document Version ID.
- New media starts QUARANTINED with scan status PENDING.
- CLEAN makes media AVAILABLE; INFECTED makes it REJECTED; FAILED remains QUARANTINED.
- CLEAN/INFECTED scan verdicts are terminal. A media item can never become AVAILABLE without CLEAN.
- Content references bind a media item to an exact content revision and role (e.g. featured, inline, attachment).
- PostgreSQL constraints enforce tenant-scoped references, unique Document-version identity, media lifecycle invariants and revision referential integrity.
- Metadata edits do not mutate the underlying Document version.

## Integration contract
Use Document's authenticated upload-session and completion application handlers to create binary documents. On successful completion, register the returned Document ID and version ID in CMS. Consume the trusted version-scan-completed event and pass its exact tenant/document/version verdict to the media application service. Do not infer CLEAN from upload completion. The CMS media service deliberately does not expose an arbitrary public download URL; delivery is a later capability and must authorize and check scan state.

## Explicitly deferred
- Image/video/audio transcoding, thumbnails and rendition workers.
- Public/private media delivery URLs and CDN.
- Collections/folders and bulk operations.
- Automated media probing and EXIF extraction.
- Scan-event consumer wiring in a host runtime.
- Media retention/deletion orchestration across Document and CMS references.

## Verification
Run `mvn -f content/pom.xml test`. Run the Document and Documents reactors independently as well. CI and live PostgreSQL migration execution must pass before merge.
