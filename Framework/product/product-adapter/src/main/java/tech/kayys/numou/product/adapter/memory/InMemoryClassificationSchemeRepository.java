package tech.kayys.syirkah.product.adapter.memory;

import tech.kayys.syirkah.product.domain.classification.ClassificationScheme;
import tech.kayys.syirkah.product.domain.classification.ClassificationSchemeId;
import tech.kayys.syirkah.product.spi.port.ClassificationSchemeRepository;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;

/** In-memory {@link ClassificationSchemeRepository} adapter. */
public final class InMemoryClassificationSchemeRepository
        implements ClassificationSchemeRepository {

    private final Map<ClassificationSchemeId, ClassificationScheme> schemesById =
            new ConcurrentHashMap<>();

    @Override
    public CompletionStage<ClassificationScheme> save(
            ClassificationScheme scheme
    ) {
        Objects.requireNonNull(scheme, "scheme cannot be null");

        schemesById.put(scheme.id(), scheme);

        return CompletableFuture.completedFuture(scheme);
    }

    @Override
    public CompletionStage<Optional<ClassificationScheme>> findById(
            ClassificationSchemeId id
    ) {
        return CompletableFuture.completedFuture(
                Optional.ofNullable(schemesById.get(id))
        );
    }

    @Override
    public CompletionStage<Optional<ClassificationScheme>> findByCode(
            String code
    ) {
        Objects.requireNonNull(code, "code cannot be null");

        return CompletableFuture.completedFuture(
                schemesById.values().stream()
                        .filter(scheme -> scheme.code().equals(code))
                        .findFirst()
        );
    }

    @Override
    public CompletionStage<Boolean> existsByCode(String code) {
        Objects.requireNonNull(code, "code cannot be null");

        return CompletableFuture.completedFuture(
                schemesById.values().stream()
                        .anyMatch(scheme -> scheme.code().equals(code))
        );
    }

    @Override
    public CompletionStage<Boolean> existsById(ClassificationSchemeId id) {
        return CompletableFuture.completedFuture(schemesById.containsKey(id));
    }

    @Override
    public CompletionStage<Void> delete(ClassificationScheme scheme) {
        Objects.requireNonNull(scheme, "scheme cannot be null");

        schemesById.remove(scheme.id());

        return CompletableFuture.completedFuture(null);
    }

    @Override
    public CompletionStage<Void> deleteById(ClassificationSchemeId id) {
        schemesById.remove(id);

        return CompletableFuture.completedFuture(null);
    }
}
