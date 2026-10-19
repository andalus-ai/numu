package tech.kayys.numou.construction.spi.quality;

import tech.kayys.numou.construction.domain.quality.NonConformanceReport;
import tech.kayys.numou.construction.domain.quality.NonConformanceReportId;
import tech.kayys.numou.foundation.domain.repository.Repository;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletionStage;

public interface NonConformanceReportRepository extends Repository<NonConformanceReport, NonConformanceReportId> {
    CompletionStage<List<NonConformanceReport>> findBySiteId(UUID siteId);
}
