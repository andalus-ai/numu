package tech.kayys.numou.workforce.spi.port;

import tech.kayys.numou.foundation.domain.repository.Repository;
import tech.kayys.numou.foundation.domain.tenant.TenantId;
import tech.kayys.numou.workforce.domain.paycomponent.PayComponent;
import tech.kayys.numou.workforce.domain.paycomponent.PayComponentId;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletionStage;

public interface PayComponentRepository extends Repository<PayComponent, PayComponentId> {

    CompletionStage<Optional<PayComponent>> findByCode(TenantId tenantId, String code);

    CompletionStage<List<PayComponent>> findByTenant(TenantId tenantId);
}
