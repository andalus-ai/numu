# Numou History
Historical knowledge foundation for chronology, events, people, collectives, places, evidence, media, and reusable timeline projections.

## Current modules
- chronology: historical date value objects, BCE/CE year numbering, Gregorian/Julian adapters, and calendar-aware range comparison.
- events: canonical historical event and period domain models, lifecycle, nested-period validation, and event-period temporal relations.
- entities: historical people, civilizations/cultures/polities/institutions, places, time-bounded names and location assertions, affiliations, event links, and typed graph relationships.
- graph-spi: tenant-aware relationship and projection-outbox persistence contracts.
- graph-application: trusted-tenant relationship use cases and lease-aware projection dispatcher/worker.
- graph-postgres-adapter: authoritative append-only PostgreSQL relationship revisions, RLS isolation, transactional projection event capture, and fenced outbox delivery.
- graph-neo4j-adapter: optional, disposable Neo4j projection for graph traversal; never the system of record.
- source: tenant-scoped historical source catalog, immutable source revisions, claim registry, precise evidence locators, and contradictory/supporting evidence links. PostgreSQL is authoritative; source binaries remain in the shared document capability.

- source/source-domain: framework-neutral source, claim, and evidence models with immutable revisions and precise evidence locators.
- source/source-spi: tenant-aware source/provenance persistence contract.
- source/source-application: trusted-tenant, authorization-aware source catalog and claim/evidence use cases.
- source/source-postgres-adapter: authoritative PostgreSQL persistence with forced RLS and append-only revision/evidence records.

M5 architecture and explicit deferrals: [HISTORY-M5-SOURCES-PROVENANCE.md](../docs/architecture/HISTORY-M5-SOURCES-PROVENANCE.md).

## Architecture
An event, person, collective, or place is recorded once. Global, regional, civilization, biography, political, economic, scientific, religious, military, cultural, and comparative timelines are projections over canonical records, not separate entity stores.
Historical valid time is distinct from record time and publication time. Calendar identity and partial-date precision are preserved. Geo's GeoPoint is reused for coordinates; History owns the historical meaning and temporal validity of location assertions.
PostgreSQL is authoritative. Neo4j is optional and rebuildable from the durable projection outbox. The Neo4j generation registry supports isolated BUILDING/READY/ACTIVE/RETAINED lifecycle records, atomic compare-and-set activation, and rollback by reactivating a retained generation. Outbox projection dual-writes to active and BUILDING/READY generations under a shared pointer lock; rebuild replay is generation-targeted and fenced; inspection and validation can target a candidate before activation. Host runtime wiring, scheduler/metrics, retention policy, and operational runbooks remain required before production enablement.

## Build
mvn -B -ntp -f geo/pom.xml install
mvn -B -ntp -f history/pom.xml verify

The history reactor is intentionally isolated from the repository's other Maven reactor roots. PostgreSQL and Neo4j adapter integration tests use Testcontainers and run in the History CI workflow. Migrations V2–V3 cover atomic event capture, lease fencing, retry accounting, and audited dead-letter replay.
