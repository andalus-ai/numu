package tech.kayys.syirkah.construction.spi.field;

import tech.kayys.syirkah.construction.domain.field.DailyFieldReport;
import tech.kayys.syirkah.construction.domain.field.DailyFieldReportId;
import tech.kayys.syirkah.foundation.domain.repository.Repository;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletionStage;

public interface DailyFieldReportRepository extends Repository<DailyFieldReport, DailyFieldReportId> {
    CompletionStage<Optional<DailyFieldReport>> findBySiteIdAndDate(UUID siteId, LocalDate reportDate);
}
