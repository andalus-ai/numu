package tech.kayys.numou.courier.domain;

import tech.kayys.numou.foundation.domain.tenant.TenantId;

import java.util.Optional;
import java.util.concurrent.CompletionStage;

/**
 * Persistence port for hubs.
 *
 * <p>Every operation includes the authenticated tenant explicitly. Implementations
 * must enforce tenant predicates in the query itself (and database row-level
 * security where available), never by filtering global query results in memory.
 */
public interface HubRepository {
    CompletionStage<Hub> save(TenantId tenantId, Hub hub);

    CompletionStage<Optional<Hub>> findById(TenantId tenantId, HubId hubId);

    CompletionStage<Optional<Hub>> findByCode(TenantId tenantId, String code);

    CompletionStage<Boolean> existsByCode(TenantId tenantId, String code);

    CompletionStage<Void> delete(TenantId tenantId, HubId hubId);
}
