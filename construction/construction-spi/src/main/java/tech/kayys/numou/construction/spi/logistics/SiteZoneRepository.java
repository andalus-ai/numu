package tech.kayys.numou.construction.spi.logistics;

import tech.kayys.numou.construction.domain.logistics.SiteZone;
import tech.kayys.numou.construction.domain.logistics.SiteZoneId;
import tech.kayys.numou.foundation.domain.repository.Repository;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletionStage;

public interface SiteZoneRepository extends Repository<SiteZone, SiteZoneId> {
    CompletionStage<List<SiteZone>> findBySiteId(UUID siteId);
}
