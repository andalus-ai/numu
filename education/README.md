# Numou Education

Indonesian education suite built as composable bounded contexts, following the repo's
`domain / spi / application / adapter` layout.

| Directory | Artifact prefix | Role |
|---|---|---|
| `school-core` | `numou-education-core` | Shared kernel: NISN/NPSN/NIK/NUPTK, jenjang, tahun ajaran, semester, score policy, cross-context ids |
| `school-management` | `numou-school-*` | SIS: school, student, teacher, classroom (rombel), attendance, report card (rapor) |
| `lms` | `numou-lms-*` | Courses, lessons, enrolment, assignments, submissions, grading |
| `school-madrasah` | `numou-madrasah-*` | Madrasah extension: NSM profile, PAI subjects, daily ibadah log, Hijri helpers |
| `school-pesantren` | `numou-pesantren-*` | Pesantren extension: santri, asrama, hafalan, halaqah, leave permits |
| `bom` | `numou-education-bom` | Version alignment |

See [docs/architecture/EDUCATION-ARCHITECTURE.md](../docs/architecture/EDUCATION-ARCHITECTURE.md).

`school-core/java/` holds an earlier Quarkus/Panache draft (`tech.kayys.school`). It is not part of the Maven build
and is kept untouched for reference.
