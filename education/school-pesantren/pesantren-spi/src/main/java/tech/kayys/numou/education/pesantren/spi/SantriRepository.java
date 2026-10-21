package tech.kayys.numou.education.pesantren.spi;

import java.util.Optional;
import java.util.concurrent.CompletionStage;
import tech.kayys.numou.education.pesantren.domain.Santri;
import tech.kayys.numou.education.pesantren.domain.SantriId;
import tech.kayys.numou.foundation.domain.repository.Repository;
import tech.kayys.numou.foundation.domain.tenant.TenantId;

public interface SantriRepository extends Repository<Santri, SantriId> {
    CompletionStage<Optional<Santri>> findByNumber(TenantId tenantId, String santriNumber);
}
