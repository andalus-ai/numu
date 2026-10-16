package tech.kayys.numou.construction.spi.site;

import tech.kayys.numou.construction.domain.site.ConstructionSite;
import tech.kayys.numou.construction.domain.site.ConstructionSiteId;
import tech.kayys.numou.foundation.domain.repository.Repository;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletionStage;

public interface ConstructionSiteRepository extends Repository<ConstructionSite, ConstructionSiteId> {
    CompletionStage<List<ConstructionSite>> findByProjectId(UUID projectId);
}
