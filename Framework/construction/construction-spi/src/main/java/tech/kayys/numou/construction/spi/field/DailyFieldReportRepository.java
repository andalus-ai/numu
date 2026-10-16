package tech.kayys.numou.construction.spi.field;

import tech.kayys.numou.construction.domain.field.DailyFieldReport;
import tech.kayys.numou.construction.domain.field.DailyFieldReportId;
import tech.kayys.numou.foundation.domain.repository.Repository;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletionStage;

public interface DailyFieldReportRepository extends Repository<DailyFieldReport, DailyFieldReportId> {
    CompletionStage<Optional<DailyFieldReport>> findBySiteIdAndDate(UUID siteId, LocalDate reportDate);
}
