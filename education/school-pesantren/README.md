# Pesantren Extension

Aggregates: `Pesantren`, `Santri` (mukim/kalong), `Dormitory` (asrama with rooms, gender-restricted),
`HafalanLog` (setoran baru / muraja'ah, validated against the 114-surah structure), `Halaqah`
(sorogan/bandongan/tahfidz), `LeavePermit` (izin pulang with late-return calculation).

Also: `SantriWallet` (uang saku with daily limit, never negative) and `DisciplineRecord` (pelanggaran with points).

Use cases: `RegisterPesantren`, `RegisterSantri`, `AssignSantriToRoom`, `RecordHafalan`,
`RequestLeavePermit` / `DecideLeavePermit` / `ReturnFromLeave` (returns days late),
`CreateHalaqah` / `JoinHalaqah`, `DepositToWallet` (opens wallet on first use) / `SpendFromWallet`, `RecordDiscipline`.

`AssignSantriToRoom` updates two aggregates; run it inside a transaction in a real persistence adapter.
Not yet implemented: leaving a dormitory room, discipline escalation rules, hafalan progress reports (juz targets).
