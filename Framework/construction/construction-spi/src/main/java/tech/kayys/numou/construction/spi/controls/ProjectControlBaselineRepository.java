package tech.kayys.syirkah.construction.spi.controls;

import tech.kayys.syirkah.construction.domain.controls.ProjectControlBaseline;
import tech.kayys.syirkah.construction.domain.controls.ProjectControlBaselineId;
import tech.kayys.syirkah.foundation.domain.repository.Repository;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletionStage;

public interface ProjectControlBaselineRepository extends Repository<ProjectControlBaseline, ProjectControlBaselineId> {
    CompletionStage<List<ProjectControlBaseline>> findByProjectId(UUID projectId);
}
