package tech.kayys.numou.education.school.application.command;

import tech.kayys.numou.education.school.domain.ReportCard;
import tech.kayys.numou.education.school.domain.ReportCardId;
import tech.kayys.numou.education.school.spi.ClassroomRepository;
import tech.kayys.numou.education.school.spi.ReportCardRepository;
import tech.kayys.numou.foundation.application.result.Result;

import java.util.Objects;
import java.util.concurrent.CompletionStage;

/** Opens a draft rapor for a rostered student; one per student, academic year and semester. */
public class OpenReportCardHandler {

    private final ClassroomRepository classrooms;
    private final ReportCardRepository reportCards;

    public OpenReportCardHandler(ClassroomRepository classrooms, ReportCardRepository reportCards) {
        this.classrooms = Objects.requireNonNull(classrooms);
        this.reportCards = Objects.requireNonNull(reportCards);
    }

    public CompletionStage<Result<ReportCardId>> handle(OpenReportCardCommand cmd) {
        return classrooms.findById(cmd.classroomId()).thenCompose(cOpt -> {
            if (cOpt.isEmpty() || !cOpt.get().getTenantId().equals(cmd.tenantId())) {
                return Outcomes.<ReportCardId>failed("school.classroom.not-found", "Classroom not found");
            }
            var classroom = cOpt.get();
            if (!classroom.hasStudent(cmd.studentId())) {
                return Outcomes.<ReportCardId>failed("school.report-card.not-rostered", "Student is not in this classroom");
            }
            return reportCards.findByStudentAndTerm(cmd.studentId(), classroom.getAcademicYear(), cmd.semester()).thenCompose(existing -> {
                if (existing.isPresent()) {
                    return Outcomes.<ReportCardId>failed("school.report-card.exists", "A report card already exists for this term");
                }
                ReportCard rc = ReportCard.draft(ReportCardId.generate(), cmd.tenantId(), cmd.studentId(), cmd.classroomId(),
                        classroom.getAcademicYear(), cmd.semester());
                return reportCards.save(rc).thenApply(saved -> Result.success(saved.getId()));
            });
        });
    }
}
