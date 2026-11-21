# Numou History Events and Periods

Framework-free Java 21 domain module for canonical historical events and named historical
periods. It depends only on the history chronology domain and the JDK.

## Ownership

- `HistoricalEvent` owns canonical event identity, editorial metadata, historical date
  range, category key, tags, and draft/published/retired lifecycle.
- `HistoricalPeriod` owns a named historical interval and an optional parent-period reference.
- `HistoricalPeriodHierarchy` validates that a child interval is contained by its parent.
- `HistoricalEventPeriodRelation` computes temporal overlap/equality. It does not infer
  causality or create duplicate event records for different timelines.
- `HistoricalChronology` owns calendar-aware comparison and containment.

## Temporal semantics

- Event/period date ranges are historical valid time.
- `recordedAt`, `updatedAt`, and `publishedAt` are record lifecycle time.
- Historical date precision is retained; year/month ranges are not silently converted to exact
  days or midpoints.
- Gregorian and Julian ranges can be compared when both adapters are registered.
- An unknown calendar adapter makes comparison unavailable rather than guessing.
- Approximate dates are retained as editorial flags; quantified uncertainty intervals belong
  with the later evidence/claims model.

## Deliberate exclusions

This milestone does not add persistence, HTTP endpoints, tenant authorization, source claims,
citations, evidence verification, person/civilization/place aggregates, media attachments,
timeline perspectives, or event-bus publication. Those concerns remain in their owning
milestones and capabilities. The domain does not depend on the generic Event envelope/outbox.

Published event content is immutable in this first lifecycle contract. Corrections and
editorial version history require an explicit revision/evidence model in a later milestone;
they must not overwrite published history in place.

## Build

From the repository root:

```bash
mvn -f history/pom.xml test
```
