package tech.kayys.numou.workforce.adapter.memory;

import tech.kayys.numou.workforce.domain.employment.Employment;
import tech.kayys.numou.workforce.domain.employment.EmploymentId;
import tech.kayys.numou.workforce.domain.employment.OrganizationRef;
import tech.kayys.numou.workforce.domain.worker.WorkerId;
import tech.kayys.numou.workforce.spi.port.EmploymentRepository;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;

public final class InMemoryEmploymentRepository implements EmploymentRepository {

    private final Map<EmploymentId, Employment> employmentsById = new ConcurrentHashMap<>();

    @Override
    public CompletionStage<Employment> save(Employment employment) {
        Objects.requireNonNull(employment, "employment cannot be null");
        employmentsById.put(employment.id(), employment);
        return CompletableFuture.completedFuture(employment);
    }

    @Override
    public CompletionStage<Optional<Employment>> findById(EmploymentId id) {
        return CompletableFuture.completedFuture(Optional.ofNullable(employmentsById.get(id)));
    }

    @Override
    public CompletionStage<List<Employment>> findByWorkerId(WorkerId workerId) {
        return CompletableFuture.completedFuture(
                employmentsById.values().stream()
                        .filter(e -> e.workerId().equals(workerId))
                        .toList()
        );
    }

    @Override
    public CompletionStage<List<Employment>> findByOrganizationRef(OrganizationRef organizationRef) {
        return CompletableFuture.completedFuture(
                employmentsById.values().stream()
                        .filter(e -> e.organization().equals(organizationRef))
                        .toList()
        );
    }

    @Override
    public CompletionStage<Boolean> existsById(EmploymentId id) {
        return CompletableFuture.completedFuture(employmentsById.containsKey(id));
    }

    @Override
    public CompletionStage<Void> delete(Employment aggregate) {
        Objects.requireNonNull(aggregate, "employment cannot be null");
        employmentsById.remove(aggregate.id());
        return CompletableFuture.completedFuture(null);
    }

    @Override
    public CompletionStage<Void> deleteById(EmploymentId id) {
        employmentsById.remove(id);
        return CompletableFuture.completedFuture(null);
    }
}
