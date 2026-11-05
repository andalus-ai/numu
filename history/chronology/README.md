# Numou History Chronology

Framework-free Java 21 domain module for dates that cannot be represented faithfully by
`java.time.LocalDate` alone.

## Current contract

- Uses astronomical year numbering internally: 1 CE = 1, 1 BCE = 0, 2 BCE = -1.
- Formats BCE/CE year-of-era labels without exposing year zero.
- Preserves calendar identity; no implicit Julian/Gregorian conversion occurs.
- Preserves YEAR, MONTH, and DAY precision without filling omitted fields.
- Carries an approximation flag and the original source expression.
- Represents closed ranges with explicit endpoints and their original precision.
- Exposes `CalendarDateConverter` as an adapter port for future calendar-specific conversion.

## Important limitations

- `HistoricalDate` validates generic field bounds only. Calendar adapters must validate
  month lengths, leap rules, eras, and calendar-specific exceptional dates.
- Nominal comparison is only valid within the same calendar and compares the supplied
  fields; it is not a conversion to a universal instant/day number.
- Partial and approximate dates are not silently converted to a midpoint or exact day.
- The parser currently accepts explicit year labels (for example, `44 BCE`, `2026 CE`,
  and `c. 1200 CE`). Month/day parsing and localized formatting require calendar adapters.
- The module is currently a standalone Maven reactor at this path. It is not yet registered
  in a repository-wide reactor because the repository has multiple reactor roots and no
  root `pom.xml`.

## Build

From this directory:

```bash
mvn test
```

## Next

Implement concrete proleptic Gregorian and Julian adapters, conversion/ordering across
calendars, interval bounds for partial dates, and property-based boundary tests before
using normalized dates for event sorting or persistence.
