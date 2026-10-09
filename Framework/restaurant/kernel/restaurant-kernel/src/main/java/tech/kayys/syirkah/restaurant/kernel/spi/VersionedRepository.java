package tech.kayys.syirkah.restaurant.kernel.spi;

import io.smallrye.mutiny.Uni;
import tech.kayys.syirkah.foundation.domain.entity.AggregateRoot;
import tech.kayys.syirkah.foundation.domain.identifier.DomainId;

import java.util.Optional;
import java.util.UUID;

/**
 * Tenant-scoped repository with optimistic concurrency.
 *
 * <p>Every method takes the tenant explicitly: there is no ambient "current tenant" to forget or to leak across
 * requests. {@code find} for another tenant's id returns empty, indistinguishable from "does not exist".
 *
 * <p>{@code save} fails with {@code ConcurrencyConflictException} if the stored version is not the one this
 * instance was loaded with (or if a new aggregate's id already exists).
 */
public interface VersionedRepository<A extends AggregateRoot<ID>, ID extends DomainId<?>> {

    Uni<Optional<A>> find(UUID tenantId, ID id);

    Uni<Void> save(UUID tenantId, A aggregate);
}
