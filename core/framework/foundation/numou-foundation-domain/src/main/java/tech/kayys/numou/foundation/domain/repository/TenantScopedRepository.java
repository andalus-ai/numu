package tech.kayys.numou.foundation.domain.repository;

import tech.kayys.numou.foundation.domain.entity.AggregateRoot;
import tech.kayys.numou.foundation.domain.identifier.DomainId;

import java.util.Optional;
import java.util.concurrent.CompletionStage;

/**
 * Repository interface for tenant-scoped aggregates (security02.md §P3-19.2).
 *
 * <p>Semantic contract: all operations are automatically bounded by the tenant
 * of the active execution context. The caller does not supply tenantId parameters.
 *
 * @param <A>  The aggregate root type, which must be TenantAware
 * @param <ID> The aggregate identifier type
 */
public interface TenantScopedRepository<
        A extends AggregateRoot<ID> & TenantAware,
        ID extends DomainId<?>>
        extends Repository<A, ID> {

    @Override
    CompletionStage<A> save(A aggregate);

    @Override
    CompletionStage<Optional<A>> findById(ID id);

    @Override
    CompletionStage<Boolean> existsById(ID id);

    @Override
    CompletionStage<Void> delete(A aggregate);

    @Override
    CompletionStage<Void> deleteById(ID id);
}
