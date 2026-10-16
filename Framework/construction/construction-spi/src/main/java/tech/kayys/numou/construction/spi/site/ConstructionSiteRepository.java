package tech.kayys.syirkah.construction.spi.site;

import tech.kayys.syirkah.construction.domain.site.ConstructionSite;
import tech.kayys.syirkah.construction.domain.site.ConstructionSiteId;
import tech.kayys.syirkah.foundation.domain.repository.Repository;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletionStage;

public interface ConstructionSiteRepository extends Repository<ConstructionSite, ConstructionSiteId> {
    CompletionStage<List<ConstructionSite>> findByProjectId(UUID projectId);
}
