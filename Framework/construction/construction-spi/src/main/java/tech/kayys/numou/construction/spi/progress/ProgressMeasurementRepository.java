package tech.kayys.numou.construction.spi.progress;

import tech.kayys.numou.construction.domain.progress.ProgressMeasurement;
import tech.kayys.numou.construction.domain.progress.ProgressMeasurementId;
import tech.kayys.numou.foundation.domain.repository.Repository;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletionStage;

public interface ProgressMeasurementRepository extends Repository<ProgressMeasurement, ProgressMeasurementId> {
    CompletionStage<List<ProgressMeasurement>> findByProjectId(UUID projectId);
    CompletionStage<List<ProgressMeasurement>> findByBoqItemId(UUID boqItemId);
}
