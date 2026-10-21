# Learning Management System

Aggregates: `Course` (with `Lesson`s and enrolment), `Assignment`, `Submission`.

Use cases: `CreateCourse`, `EnrollStudentInCourse`, `CreateAssignment`, `SubmitAssignment`
(enrolment required, deadline and late policy enforced, resubmission until graded), `GradeSubmission`,
`CreateQuiz` / `SubmitQuizAttempt` (multiple choice, automatic grading, attempt limits).

Lesson content is a reference (`contentRef`) to the document/storage capability, not stored binary.
Not yet implemented: question bank and randomisation, discussions, gradebook rollup into the SIS report card.
