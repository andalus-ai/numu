# Numou History Entities

Framework-free historical entity domain contracts for people, collectives and places.

## Domain ownership

- `HistoricalPerson`: stable person identity, primary and alternative names, optional birth/death date assertions, biography and revision metadata.
- `HistoricalCollective`: civilizations, cultures, states, empires, kingdoms, dynasties, institutions and other catalogued collective kinds.
- `HistoricalPlace`: stable place identity with time-bounded names and location assertions, optional parent-place reference and optional existence range.
- `HistoricalAffiliation`: time-bounded person-to-collective relationship.
- `HistoricalEventParticipant` and `HistoricalEventPlace`: links from canonical event IDs to canonical people, collectives and places.
- `HistoricalEntityTemporalPolicy`: calendar-aware validation for biography ordering, affiliation intervals and place assertions.

## Important semantics

- Names, affiliations, coordinates and boundaries can change over historical time; they are assertions associated with stable identities.
- Place coordinates reuse Geo's public WGS84 `GeoPoint`; History does not duplicate coordinate validation or geodesic behavior.
- Location precision labels describe the editorial meaning of a point (feature, approximate location, representative centroid), not a numerical accuracy guarantee.
- Event and entity links use typed stable IDs. They do not embed or mutate another capability's aggregate.
- A missing birth/death/existence range means unknown or not asserted; it does not mean the person/place never existed.
- A missing validity range on a name or coordinate assertion means no temporal interval has been asserted; it does not mean the assertion is timeless.
- Partial-date overlap is not treated as a contradiction. The temporal policy rejects only a demonstrably reversed order or an assertion demonstrably outside a known interval.
- Collective/place parent references are structural links only. Repository/application boundaries must reject cycles and authorize both records before persisting relationships.
- Source attribution, conflicting identity claims, confidence/evidence, territorial boundary geometries, source datasets, persistence and authorization are deferred to their owning milestones (especially M5/M8/M9/M11).

## Build

The history reactor uses Geo's domain artifact. Install Geo first, then run:

```bash
mvn -B -ntp -f geo/pom.xml install
mvn -B -ntp -f history/pom.xml verify
```

The History domain remains free of Geo adapters, database drivers, transport frameworks and map-provider SDKs.
