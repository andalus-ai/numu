package tech.kayys.numou.education.school.application.command;

import tech.kayys.numou.education.school.domain.ReportCardId;
import tech.kayys.numou.education.school.spi.ReportCardRepository;
import tech.kayys.numou.foundation.application.result.Result;

import java.util.Objects;
import java.util.concurrent.CompletionStage;

public class FinalizeReportCardHandler {

    private final ReportCardRepository reportCards;

    public FinalizeReportCardHandler(ReportCardRepository reportCards) {
        this.reportCards = Objects.requireNonNull(reportCards);
    }

    public CompletionStage<Result<ReportCardId>> handle(FinalizeReportCardCommand cmd) {
        return reportCards.findById(cmd.reportCardId()).thenCompose(opt -> {
            if (opt.isEmpty() || !opt.get().getTenantId().equals(cmd.tenantId())) {
                return Outcomes.<ReportCardId>failed("school.report-card.not-found", "Report card not found");
            }
            var rc = opt.get();
            try {
                rc.finalizeCard();
            } catch (IllegalStateException e) {
                return Outcomes.<ReportCardId>failed("school.report-card.cannot-finalize", e.getMessage());
            }
            return reportCards.save(rc).thenApply(saved -> Result.success(saved.getId()));
        });
    }
}
