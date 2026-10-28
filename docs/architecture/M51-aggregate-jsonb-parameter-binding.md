# M51 — Aggregate JSONB parameter binding

## Observed failure

The Mobility PostgreSQL integration tests reported `Corrupt wallet document` while reading a wallet immediately after creating or crediting it. Jackson's root input was a JSON string containing an object (`"{\"id\":...}"`) rather than an object node (`{"id":...}`). The failure occurs in the shared `PgDocumentRepository.read` path, so the persistence boundary—not wallet domain behavior—is the appropriate owner.

## Change

Bind the serialized snapshot parameter explicitly as SQL text using Hibernate's `StandardBasicTypes.STRING` before the existing SQL `CAST(?8 AS jsonb)`. This avoids relying on native-query parameter inference for a value that is already serialized JSON text. The database cast remains responsible for parsing and storing a JSONB object. No schema, domain snapshot, or public API changes are intended.

## Verification

The existing `mobility/mobility-api` `WalletPostgresIntegrationTest` exercises persisted replay, concurrent same-reference credits, idempotency conflicts, rollback atomicity, and tenant isolation against PostgreSQL. CI must confirm these pass with the explicit parameter type, and downstream capability checks must be reviewed before this change is considered verified.

## Compatibility and risk

This changes only the binding type of the serialized document parameter. It does not add a data migration or silently unwrap existing string-valued JSONB documents. If CI shows historical string-valued documents, any data repair must be designed as a separate, auditable migration rather than hidden in the generic read path.
