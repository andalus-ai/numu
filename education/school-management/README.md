# School Management (SIS)

Aggregates: `School`, `Student`, `Teacher`, `Classroom` (rombel), `AttendanceSheet`, `ReportCard`.

Use cases: `RegisterSchool`, `RegisterStudent` (duplicate-NISN check), `CreateClassroom`,
`EnrollStudent` (capacity, same school, one classroom per academic year), `MarkAttendance`
(one sheet per classroom per day, rostered students only, lockable),
`ScheduleLesson` (timetable; blocks double-booked teachers and classrooms),
`OpenReportCard` / `RecordReportCard` / `FinalizeReportCard` (rapor lifecycle; finalized cards are immutable),
`IssueFeeInvoice` / `RecordFeePayment` (monthly SPP, instalments, no overpayment, void only before payment).

Not yet implemented: PPDB admissions, grade-book rollup from LMS into the rapor, fee reminders, payment-gateway integration
(the finance module is the intended integration point).
