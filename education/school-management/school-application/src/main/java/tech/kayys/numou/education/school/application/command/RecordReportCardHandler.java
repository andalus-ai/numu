package tech.kayys.numou.education.school.application.command;

import tech.kayys.numou.education.school.domain.ReportCardId;
import tech.kayys.numou.education.school.spi.ReportCardRepository;
import tech.kayys.numou.foundation.application.result.Result;

import java.util.Objects;
import java.util.concurrent.CompletionStage;

/** Fills a draft report card with subject results, attendance totals and the homeroom note. */
public class RecordReportCardHandler {

    private final ReportCardRepository reportCards;

    public RecordReportCardHandler(ReportCardRepository reportCards) {
        this.reportCards = Objects.requireNonNull(reportCards);
    }

    public CompletionStage<Result<ReportCardId>> handle(RecordReportCardCommand cmd) {
        return reportCards.findById(cmd.reportCardId()).thenCompose(opt -> {
            if (opt.isEmpty() || !opt.get().getTenantId().equals(cmd.tenantId())) {
                return Outcomes.<ReportCardId>failed("school.report-card.not-found", "Report card not found");
            }
            var rc = opt.get();
            try {
                if (cmd.subjects() != null) cmd.subjects().forEach(rc::recordSubject);
                rc.recordAttendance(cmd.sick(), cmd.permitted(), cmd.absent());
                if (cmd.homeroomNote() != null) rc.writeHomeroomNote(cmd.homeroomNote());
            } catch (IllegalStateException | IllegalArgumentException e) {
                return Outcomes.<ReportCardId>failed("school.report-card.cannot-edit", e.getMessage());
            }
            return reportCards.save(rc).thenApply(saved -> Result.success(saved.getId()));
        });
    }
}
