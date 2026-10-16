package tech.kayys.numou.workforce.adapter.memory;

import tech.kayys.numou.workforce.domain.qualification.Qualification;
import tech.kayys.numou.workforce.domain.qualification.QualificationId;
import tech.kayys.numou.workforce.spi.port.QualificationRepository;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;

public final class InMemoryQualificationRepository implements QualificationRepository {

    private final Map<QualificationId, Qualification> qualificationsById = new ConcurrentHashMap<>();
    private final Map<String, QualificationId> idsByCode = new ConcurrentHashMap<>();

    @Override
    public CompletionStage<Qualification> save(Qualification qualification) {
        Objects.requireNonNull(qualification, "qualification cannot be null");
        qualificationsById.put(qualification.id(), qualification);
        idsByCode.put(qualification.getCode(), qualification.id());
        return CompletableFuture.completedFuture(qualification);
    }

    @Override
    public CompletionStage<Optional<Qualification>> findById(QualificationId id) {
        return CompletableFuture.completedFuture(Optional.ofNullable(qualificationsById.get(id)));
    }

    @Override
    public CompletionStage<Optional<Qualification>> findByCode(String code) {
        var id = idsByCode.get(code);
        return CompletableFuture.completedFuture(
                id == null ? Optional.empty() : Optional.ofNullable(qualificationsById.get(id))
        );
    }

    @Override
    public CompletionStage<Boolean> existsById(QualificationId id) {
        return CompletableFuture.completedFuture(qualificationsById.containsKey(id));
    }

    @Override
    public CompletionStage<Void> delete(Qualification aggregate) {
        Objects.requireNonNull(aggregate, "qualification cannot be null");
        qualificationsById.remove(aggregate.id());
        idsByCode.remove(aggregate.getCode());
        return CompletableFuture.completedFuture(null);
    }

    @Override
    public CompletionStage<Void> deleteById(QualificationId id) {
        var removed = qualificationsById.remove(id);
        if (removed != null) {
            idsByCode.remove(removed.getCode());
        }
        return CompletableFuture.completedFuture(null);
    }
}
