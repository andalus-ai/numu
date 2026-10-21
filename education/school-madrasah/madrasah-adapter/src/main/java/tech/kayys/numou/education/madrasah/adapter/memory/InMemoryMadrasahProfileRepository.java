package tech.kayys.numou.education.madrasah.adapter.memory;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;
import tech.kayys.numou.education.core.SchoolId;
import tech.kayys.numou.education.madrasah.domain.MadrasahProfile;
import tech.kayys.numou.education.madrasah.domain.MadrasahProfileId;
import tech.kayys.numou.education.madrasah.spi.MadrasahProfileRepository;

public class InMemoryMadrasahProfileRepository implements MadrasahProfileRepository {
    private final Map<MadrasahProfileId, MadrasahProfile> store = new ConcurrentHashMap<>();

    @Override public CompletionStage<MadrasahProfile> save(MadrasahProfile e) { store.put(e.getId(), e); return CompletableFuture.completedFuture(e); }
    @Override public CompletionStage<Optional<MadrasahProfile>> findById(MadrasahProfileId id) { return CompletableFuture.completedFuture(Optional.ofNullable(store.get(id))); }
    @Override public CompletionStage<Boolean> existsById(MadrasahProfileId id) { return CompletableFuture.completedFuture(store.containsKey(id)); }
    @Override public CompletionStage<Void> delete(MadrasahProfile e) { store.remove(e.getId()); return CompletableFuture.completedFuture(null); }
    @Override public CompletionStage<Void> deleteById(MadrasahProfileId id) { store.remove(id); return CompletableFuture.completedFuture(null); }
    @Override
    public CompletionStage<Optional<MadrasahProfile>> findBySchool(SchoolId schoolId) {
        return CompletableFuture.completedFuture(store.values().stream().filter(p -> p.getSchoolId().equals(schoolId)).findFirst());
    }
}
