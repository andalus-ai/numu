package tech.kayys.numou.foundation.domain.repository;

import tech.kayys.numou.foundation.domain.entity.AggregateRoot;
import tech.kayys.numou.foundation.domain.identifier.DomainId;

import java.util.Optional;
import java.util.concurrent.CompletionStage;

/**
 * Generic repository interface for aggregate roots.
 *
 * @param <A> The aggregate root type
 * @param <ID> The aggregate identifier type
 */
public interface Repository<A extends AggregateRoot<ID>, ID extends DomainId<?>> {

    CompletionStage<A> save(A aggregate);

    CompletionStage<Optional<A>> findById(ID id);

    CompletionStage<Boolean> existsById(ID id);

    CompletionStage<Void> delete(A aggregate);

    CompletionStage<Void> deleteById(ID id);
}
