package tech.kayys.syirkah.product.application.support;

import tech.kayys.syirkah.product.domain.classification.ClassificationScheme;
import tech.kayys.syirkah.product.domain.classification.ClassificationSchemeId;
import tech.kayys.syirkah.product.spi.port.ClassificationSchemeRepository;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

/** Test double for {@link ClassificationSchemeRepository}. */
public final class InMemoryClassificationSchemeRepository
        implements ClassificationSchemeRepository {

    private final Map<ClassificationSchemeId, ClassificationScheme> schemesById =
            new LinkedHashMap<>();

    @Override
    public CompletionStage<ClassificationScheme> save(
            ClassificationScheme scheme
    ) {
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
        return CompletableFuture.completedFuture(
                schemesById.values().stream()
                        .filter(scheme -> scheme.code().equals(code))
                        .findFirst()
        );
    }

    @Override
    public CompletionStage<Boolean> existsByCode(String code) {
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
        schemesById.remove(scheme.id());

        return CompletableFuture.completedFuture(null);
    }

    @Override
    public CompletionStage<Void> deleteById(ClassificationSchemeId id) {
        schemesById.remove(id);

        return CompletableFuture.completedFuture(null);
    }
}
