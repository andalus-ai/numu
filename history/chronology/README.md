# Numou History Chronology

Framework-free Java 21 domain module for dates that cannot be represented faithfully by
`java.time.LocalDate` alone.

## Current contract

- Uses astronomical year numbering: 1 CE = 1, 1 BCE = 0, 2 BCE = -1.
- Formats BCE/CE year-of-era labels without exposing year zero.
- Preserves calendar identity; no implicit calendar conversion occurs in value objects.
- Preserves YEAR, MONTH, and DAY precision without filling omitted fields.
- Carries an approximation flag and the original source expression.
- Represents closed historical ranges with explicit endpoints and their original precision.
- Provides proleptic Gregorian and Julian calendar adapters.
- Normalizes exact and partial dates onto an integer absolute-day axis using Julian Day Number
  arithmetic. Conversion uses checked arithmetic; values outside the signed-long day axis fail
  rather than wrap.
- Compares dates and closed date ranges across registered calendars using inclusive precision bounds.

## Semantics and limitations

- The Gregorian and Julian adapters are **proleptic**: they apply each calendar's rules
  consistently across the full supported year range. They do not model jurisdiction-specific
  historical adoption dates or the 1582 transition as a universal civil event.
- Astronomical year numbering is used by both adapters, including year zero.
- YEAR and MONTH dates normalize to inclusive first/last-day bounds. Comparing a year with a
  month in that year therefore returns OVERLAPS, not an invented point ordering.
- The approximation flag preserves editorial uncertainty but does not quantify it. It does not
  expand normalized bounds. A later evidence/uncertainty model must supply explicit earliest
  and latest bounds where a source says, for example, "around 1200".
- Nominal comparison remains same-calendar-only. Cross-calendar comparison must go through
  `HistoricalChronology` and registered adapters.
- Generic `HistoricalDate` field bounds allow month 13 for future calendar families; Gregorian
  and Julian adapters correctly reject it.
- The parser currently accepts explicit year labels (for example, `44 BCE`, `2026 CE`,
  and `c. 1200 CE`). Month/day parsing and localized formatting require calendar adapters.
- The shared `history/pom.xml` reactor includes chronology and historical events.

## Build

From the repository root:

```bash
mvn -f history/pom.xml test
```

## Next

Add additional calendar families (for example, Hijri and Buddhist calendar adapters), explicit
uncertainty intervals derived from claims/evidence, and persistence serialization contracts.
