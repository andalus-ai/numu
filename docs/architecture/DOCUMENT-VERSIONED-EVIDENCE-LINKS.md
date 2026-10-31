# Document versioned evidence links

## Purpose

Domain capabilities reference reports, drawings, approvals and other evidence without storing file bytes or reaching into the document adapter's persistence model. The Document capability owns document identity and version identity; a consuming capability owns the meaning of the relationship.

## Contract

- `DocumentLinkRepository` is a public application port. Every operation is tenant-scoped.
- A link can optionally pin an immutable `DocumentVersionId`. New evidence workflows should pin a version so a later upload cannot silently change the evidence being reviewed.
- Existing links remain readable with a null version for migration compatibility. A null version is not proof of a signed or accepted artifact.
- PostgreSQL composite foreign keys enforce that the linked document and pinned version belong to the supplied tenant and that the version belongs to the same document.
- Links are append-only at both the application contract and database boundary. Migration V4 rejects direct UPDATE and DELETE operations; create a new link to record a changed relationship.
- Because link deletion is rejected, a physical delete of a document with existing links is also rejected when its foreign-key cascade attempts to delete those links. Use the document lifecycle/status policy rather than hard-deleting evidence-bearing documents.
- Migration V5 prevents duplicate relationship rows for both legacy unpinned links and version-pinned links. The repository uses `ON CONFLICT DO NOTHING` against the corresponding uniqueness rule so a retry of the same relationship is a no-op rather than a duplicate insert.
- V5 intentionally fails if existing duplicate relationships are found. Historical evidence is not automatically deleted or consolidated; operators must review duplicates before migration.
- Aggregate type and ID are opaque stable references, not permission checks. Callers must authorize both document access and access to the target aggregate before exposing links.

## Compatibility and scope

Migration V3 adds a nullable version reference to the existing `document_links` table and leaves existing rows intact. Migration V4 enforces append-only link behavior, and V5 adds uniqueness after a duplicate preflight. The shared repository contract still does not provide a consultancy-specific evidence workflow or prove that a document is digitally signed, legally valid, or Sharia-compliant. Those claims require their own verification and qualified review.


## Actor attribution

Document links support nullable `linked_by` attribution. New version-pinned evidence links created through an actor-aware factory persist the initiating actor; legacy links and historical rows may remain null because their creator cannot be inferred. The nullable field preserves existing data while allowing cross-capability workflows to return an auditable actor on newly created links.
