# Education Core (shared kernel)

Pure-Java value objects shared by every education context. No persistence, no framework.

- Identifiers: `Nisn` (10 digits), `Npsn` (8), `Nik` (16), `Nuptk` (16)
- `EducationLevel` (PAUD, TK, RA, SD, MI, SMP, MTS, SMA, SMK, MA) with grade ranges and madrasah flag
- `GradeLevel`, `AcademicYear` (July start), `Semester`, `Curriculum`
- `Score` (0-100) and configurable `ScoreBands` for predikat A-D
- Cross-context ids: `SchoolId`, `StudentId`, `TeacherId`, `ClassroomId`

Other contexts reference each other only through these ids, never through each other's aggregates.
