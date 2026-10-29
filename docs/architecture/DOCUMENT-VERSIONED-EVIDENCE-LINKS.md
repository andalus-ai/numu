# Document versioned evidence links

## Purpose

Domain capabilities need to reference signed reports, drawings, approvals and other evidence without storing file bytes or reaching into the document adapter's persistence model. The Document capability owns document identity and version identity; a consuming capability owns the meaning of the relationship.

## Contract

- `DocumentLinkRepository` is a public application port. Every operation is tenant-scoped.
- A link can optionally pin an immutable `DocumentVersionId`. New evidence workflows should pin a version so a later upload cannot silently change the evidence being reviewed.
- Existing links remain readable with a null version for migration compatibility. A null version is not proof of a signed or accepted artifact.
- PostgreSQL composite foreign keys enforce that the linked document and pinned version belong to the supplied tenant and that the version belongs to the same document.
- Links are append-only by contract: update an evidence relationship by creating a new link rather than retargeting an existing one.
- Aggregate type and ID are opaque stable references, not permission checks. Callers must authorize both document access and access to the target aggregate before exposing links.

## Compatibility and scope

Migration V3 adds a nullable version reference to the existing `document_links` table and leaves existing rows intact. This milestone provides the repository contract and PostgreSQL adapter; it does not yet add a consultancy-specific evidence workflow or assert that a document is digitally signed, legally valid, or Sharia-compliant. Those claims require their own verification and qualified review.
