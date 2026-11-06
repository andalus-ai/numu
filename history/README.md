# Numou History
Historical knowledge foundation for chronology, events, people, collectives, places, evidence, media, and reusable timeline projections.

## Current modules
- chronology: historical date value objects, BCE/CE year numbering, Gregorian/Julian adapters, and calendar-aware range comparison.
- events: canonical historical event and period domain models, lifecycle, nested-period validation, and event-period temporal relations.
- entities: historical people, civilizations/cultures/polities/institutions, places, time-bounded names and location assertions, affiliations, event links, and typed graph relationships.
- graph-spi: tenant-aware relationship and projection-outbox persistence contracts.
- graph-application: trusted-tenant relationship use cases and lease-aware projection dispatcher/worker.
- graph-postgres-adapter: append-only PostgreSQL relationship revisions, RLS isolation, transactional projection event capture, and fenced outbox delivery.

## Architecture
An event, person, collective, or place is recorded once. Global, regional, civilization, biography, political, economic, scientific, religious, military, cultural, and comparative timelines are projections over canonical records, not separate entity stores.
Historical valid time is distinct from record time and publication time. Calendar identity and partial-date precision are preserved. Geo's GeoPoint is reused for coordinates; History owns the historical meaning and temporal validity of location assertions.

## Build
mvn -B -ntp -f geo/pom.xml install
mvn -B -ntp -f history/pom.xml verify

The history reactor is intentionally isolated from the repository's other Maven reactor roots. PostgreSQL adapter integration tests use Testcontainers and run in the History CI workflow. Migrations V2–V3 cover atomic event capture, lease fencing, retry accounting, and audited dead-letter replay.