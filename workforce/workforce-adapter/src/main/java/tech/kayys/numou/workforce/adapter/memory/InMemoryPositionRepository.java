package tech.kayys.numou.workforce.adapter.memory;

import tech.kayys.numou.workforce.domain.employment.OrganizationRef;
import tech.kayys.numou.workforce.domain.position.Position;
import tech.kayys.numou.workforce.domain.position.PositionId;
import tech.kayys.numou.workforce.spi.port.PositionRepository;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;

public final class InMemoryPositionRepository implements PositionRepository {

    private final Map<PositionId, Position> positionsById = new ConcurrentHashMap<>();

    @Override
    public CompletionStage<Position> save(Position position) {
        Objects.requireNonNull(position, "position cannot be null");
        positionsById.put(position.id(), position);
        return CompletableFuture.completedFuture(position);
    }

    @Override
    public CompletionStage<Optional<Position>> findById(PositionId id) {
        return CompletableFuture.completedFuture(Optional.ofNullable(positionsById.get(id)));
    }

    @Override
    public CompletionStage<List<Position>> findByOrganizationRef(OrganizationRef organizationRef) {
        return CompletableFuture.completedFuture(
                positionsById.values().stream()
                        .filter(p -> p.organization().equals(organizationRef))
                        .toList()
        );
    }

    @Override
    public CompletionStage<Boolean> existsById(PositionId id) {
        return CompletableFuture.completedFuture(positionsById.containsKey(id));
    }

    @Override
    public CompletionStage<Void> delete(Position aggregate) {
        Objects.requireNonNull(aggregate, "position cannot be null");
        positionsById.remove(aggregate.id());
        return CompletableFuture.completedFuture(null);
    }

    @Override
    public CompletionStage<Void> deleteById(PositionId id) {
        positionsById.remove(id);
        return CompletableFuture.completedFuture(null);
    }
}
