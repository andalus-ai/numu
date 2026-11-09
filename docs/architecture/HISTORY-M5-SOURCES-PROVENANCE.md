# History M5 — Sources, Claims and Evidence Provenance

## Status

M5.4 implementation increment. This document describes the source catalog, claim-evidence provenance, qualitative source assessments, and append-only editorial review contracts; it does not claim that all planned M5 capabilities are complete.

## Ownership and boundaries

- History owns source metadata, source revisions, historical propositions (claims), and claim-to-source evidence associations.
- The canonical event, person, collective, and place capabilities remain owners of their records. A claim refers to a subject through a typed `(subjectKind, subjectId)` reference; the host authorization adapter must validate that reference.
- The shared document/media capability owns binary content, retention, storage providers, and access to files. History stores metadata and stable references, not duplicate binary blobs.
- PostgreSQL is authoritative. This slice does not add a second graph store, broker, workflow engine, document store, or identity model.
- Tenant identity is resolved from trusted host context. Request payloads never select the persistence tenant.

## Implemented contracts

### Source catalog

A source records:
- stable source identity and kind (book, manuscript, archive/government record, academic publication, website, oral history, image/video/audio/map, archaeological artifact, or other);
- title, subtitle, description, publisher, language tag, and canonical URI;
- the original publication-date expression as supplied (for example `c. 1200 CE`), without pretending it is a fully resolved chronology date;
- monotonically increasing metadata revision and ACTIVE/ARCHIVED lifecycle.

Source revisions are immutable rows. The source head is advanced with compare-and-set against the expected revision. Archived sources cannot be revised. Source registration and each revision are tenant-scoped.

### Claims and evidence

A historical claim is a proposition, not a canonical event/entity fact. It references a typed subject and retains its own statement and identity.

Each evidence association is independently recorded with:
- relation: `SUPPORTS`, `CONTRADICTS`, `CONTEXTUALIZES`, or `MENTIONS`;
- locator type: page, paragraph, section, timecode, URL fragment, coordinate, object mark, or other;
- locator value and optional excerpt;
- recording timestamp.

Contradictory evidence is preserved alongside supporting evidence. The system does not calculate a truth score, collapse conflicting accounts, or treat repeated citations as independent corroboration.

### Source assessments and editorial review

- Assessments are append-only and scoped to a source plus one explicit dimension: authenticity, reliability, independence, provenance, completeness, bias, or condition.
- Each assessment records a qualitative conclusion (verified, plausible, uncertain, doubtful, unverified, conflicting), rationale, methodology identifier and version, assessor reference, and timestamp. No universal numeric reliability score is exposed.
- Assessment and review actors are passed to host authorization ports; the host must bind those references to the authenticated actor and check the relevant permission.
- Editorial decisions (accept, request changes, reject) are separate immutable review records. A later review never overwrites the assessment or earlier editorial decisions.
- PostgreSQL enforces tenant-scoped foreign keys, forced RLS, and append-only triggers for both assessment and review tables.

## Editions, contributors and archival holdings

- A source remains the intellectual/work-level record. A `SourceEdition` is a distinct publication or manifestation with its own designation, publisher, place, date expression, language, identifier and optional canonical URI. Publication dates remain text so approximate expressions are not silently normalized.
- `SourceContributor` records source-specific attribution and role (author, editor, translator, compiler, oral informant, etc.). It stores a display name plus an optional external authority reference; History does not own canonical person identity. Attribution notes preserve uncertainty.
- `ArchivalHolding` records a particular institutional custody/catalogue instance, including institution reference/name, repository, collection, shelfmark, holding type, access conditions and optional catalogue URI.
- `ArchivalCatalogIdentifier` is scoped to a holding and assigning scheme/authority. Identifiers are not assumed globally unique across institutions.
- These records are append-only, tenant-scoped and independently addressable. Catalogue identifier reads are scoped to both source and holding; a holding lookup is likewise source-scoped.

## Source-to-source relationships

- Source relationships are directed and preserve the direction from the citing/deriving source to the related source.
- Supported relation types include citation, translation, editing, derivation, reproduction, response, contradiction, supersession and general relation.
- An optional locator (type and value supplied together) and explanatory note preserve where and why the relationship is asserted.
- These links are bibliographic/provenance relationships between source records. They do not replace `ClaimEvidence`, which connects one source to one historical claim.
- Self-links are rejected. Both source endpoints must exist in the same tenant. Outgoing and incoming queries are separate, bounded, tenant-scoped and authorization-checked for both endpoints.
- Relationship records are append-only; no citation graph is treated as a truth score or canonical event/person/place graph.

## Persistence and security

- The PostgreSQL adapter sets transaction-local `numou.tenant_id` from the trusted application argument for every operation.
- Forced row-level security protects source heads, immutable source revisions, claims, evidence associations, assessments, editorial reviews, contributors, editions, holdings and catalogue identifiers.
- Composite tenant foreign keys prevent evidence from linking a claim or source owned by another tenant.
- Source revisions, claims, evidence, assessments, editorial reviews, contributor attributions, editions, holdings and catalogue identifiers are append-only at the database trigger boundary. The mutable source head is only a pointer to the latest immutable revision.
- Revision advancement and source revision insertion occur in one transaction.
- Evidence insertion relies on tenant-scoped foreign keys to ensure both the claim and source exist in the same tenant.

The runtime role must be a non-owner, non-superuser, `NOBYPASSRLS` principal with only the table privileges required by the adapter. Migration ownership and runtime credentials must remain separate.

## Build and verification

Run the History reactor using the existing CI entry point:

```bash
mvn -B -ntp -f geo/pom.xml install
mvn -B -ntp -f history/pom.xml verify
```

The focused History workflow runs Java 21 and the PostgreSQL Testcontainers suite. The source adapter integration test creates a restricted PostgreSQL runtime role and verifies tenant isolation, source revision compare-and-set, duplicate registration, claims, evidence, qualitative assessments, editorial reviews, editions, contributor attributions, archival holdings, catalog identifiers, directed source relationships and append-only enforcement.

## Deliberately deferred


- source ingestion, OCR/transcription, content extraction and shared document-store integration;
- graph projections, citation discovery and cross-source independence analysis;
- public/shared corpus ingestion and its separately privileged authorization boundary;
- REST/GraphQL APIs, search indexing, ranking and discovery;
- cross-tenant sharing or public-source visibility policies.

These should be separate M5 increments. In particular, no universal numeric reliability score should be introduced without a documented, explainable assessment model.
