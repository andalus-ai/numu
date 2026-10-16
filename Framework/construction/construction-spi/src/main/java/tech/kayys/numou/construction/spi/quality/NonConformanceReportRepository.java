package tech.kayys.syirkah.construction.spi.quality;

import tech.kayys.syirkah.construction.domain.quality.NonConformanceReport;
import tech.kayys.syirkah.construction.domain.quality.NonConformanceReportId;
import tech.kayys.syirkah.foundation.domain.repository.Repository;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletionStage;

public interface NonConformanceReportRepository extends Repository<NonConformanceReport, NonConformanceReportId> {
    CompletionStage<List<NonConformanceReport>> findBySiteId(UUID siteId);
}
