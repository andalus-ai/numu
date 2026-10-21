# Education suite architecture

Follows [DOMAIN-CAPABILITY-MATRIX](DOMAIN-CAPABILITY-MATRIX.md): a neutral core with extensions that depend inward.

```
            numou-education-core  (shared kernel, ids, value objects)
             /        |         \
  school-management  lms    (no dependency between them)
             \
   school-madrasah   school-pesantren   <- extensions, reference school/student by id only
```

## Rules
1. Contexts depend on `numou-education-core` and the foundation, never on each other's aggregates.
2. Madrasah and pesantren attach to general-school concepts through `SchoolId`/`StudentId`/`TeacherId`.
3. A pesantren may run without a formal school; a madrasah profile always points to a school.
4. Policies that vary per school (score bands/KKM, curriculum) are data, not constants.
5. Finalized records (report cards, locked attendance, graded submissions) are historical facts.

## Indonesian specifics
NISN/NPSN/NIK/NUPTK/NSM identifiers, jenjang and grade ranges, July-start academic year with ganjil/genap semesters,
rapor knowledge/skill scores, hadir/sakit/izin/alpa attendance, PAI subject group, mukim/kalong santri,
setoran/muraja'ah hafalan, sorogan/bandongan study methods.

## Integration points (not yet built)
Fee billing (SPP) should use the finance module; notifications to wali via `numou-communication`;
identities and roles via identity/security; timetable via the scheduling context (rooms and teachers as resources).
