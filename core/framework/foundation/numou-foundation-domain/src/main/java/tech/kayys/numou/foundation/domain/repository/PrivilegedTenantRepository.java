package tech.kayys.numou.foundation.domain.repository;

import tech.kayys.numou.foundation.domain.entity.AggregateRoot;
import tech.kayys.numou.foundation.domain.identifier.DomainId;
import tech.kayys.numou.foundation.domain.tenant.TenantId;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletionStage;

/**
 * Privileged repository interface for administrative or cross-tenant operations (security02.md §P3-19.12).
 *
 * <p>Only explicitly authorized platform-level operations may use this repository.
 *
 * @param <A>  The aggregate root type
 * @param <ID> The aggregate identifier type
 */
public interface PrivilegedTenantRepository<
        A extends AggregateRoot<ID> & TenantAware,
        ID extends DomainId<?>>
        extends Repository<A, ID> {

    CompletionStage<Optional<A>> findByTenant(
            TenantId tenantId,
            ID id);

    CompletionStage<List<A>> findAllByTenant(
            TenantId tenantId);
}
