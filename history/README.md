# Numou History

Historical knowledge foundation for chronology, events, periods, evidence, geography, media,
and reusable timeline projections.

## Current modules

- `chronology`: historical date value objects, BCE/CE year numbering, Gregorian/Julian
  adapters, and calendar-aware range comparison.
- `events`: canonical historical event and period domain models, lifecycle, nested-period
  validation, and event-period temporal relations.

## Architecture

A historical event is recorded once. Global, regional, civilization, biography, political,
economic, scientific, religious, military, cultural, and comparative timelines are projections
over canonical records, not separate event stores.

Historical valid time is distinct from record time and publication time. Calendar identity and
partial-date precision are preserved. The generic Event envelope/outbox is an integration
mechanism and is not a second owner of historical facts.

## Build

```bash
mvn -f history/pom.xml test
```

The history reactor is intentionally isolated from the repository's other Maven reactor roots.
