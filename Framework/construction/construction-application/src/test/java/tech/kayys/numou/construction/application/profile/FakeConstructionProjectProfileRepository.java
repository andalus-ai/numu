package tech.kayys.syirkah.construction.application.profile;

import tech.kayys.syirkah.construction.domain.profile.ConstructionProjectProfile;
import tech.kayys.syirkah.construction.domain.profile.ConstructionProjectProfileId;
import tech.kayys.syirkah.construction.spi.profile.ConstructionProjectProfileRepository;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;

final class FakeConstructionProjectProfileRepository implements ConstructionProjectProfileRepository {
    private final Map<ConstructionProjectProfileId, ConstructionProjectProfile> store = new ConcurrentHashMap<>();

    @Override
    public CompletionStage<ConstructionProjectProfile> save(ConstructionProjectProfile profile) {
        Objects.requireNonNull(profile);
        store.put(profile.id(), profile);
        return CompletableFuture.completedFuture(profile);
    }

    @Override
    public CompletionStage<Optional<ConstructionProjectProfile>> findById(ConstructionProjectProfileId id) {
        return CompletableFuture.completedFuture(Optional.ofNullable(store.get(id)));
    }

    @Override
    public CompletionStage<Optional<ConstructionProjectProfile>> findByProjectId(UUID projectId) {
        return CompletableFuture.completedFuture(
                store.values().stream().filter(p -> p.projectId().equals(projectId)).findFirst()
        );
    }

    @Override
    public CompletionStage<Boolean> existsById(ConstructionProjectProfileId id) {
        return CompletableFuture.completedFuture(store.containsKey(id));
    }

    @Override
    public CompletionStage<Void> delete(ConstructionProjectProfile profile) {
        store.remove(profile.id());
        return CompletableFuture.completedFuture(null);
    }

    @Override
    public CompletionStage<Void> deleteById(ConstructionProjectProfileId id) {
        store.remove(id);
        return CompletableFuture.completedFuture(null);
    }
}
