package tech.kayys.numou.workforce.adapter.memory;

import tech.kayys.numou.workforce.domain.compensation.CompensationTerms;
import tech.kayys.numou.workforce.domain.compensation.CompensationTermsId;
import tech.kayys.numou.workforce.domain.employment.EmploymentId;
import tech.kayys.numou.workforce.spi.port.CompensationTermsRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;

public class InMemoryCompensationTermsRepository implements CompensationTermsRepository {

    private final Map<CompensationTermsId, CompensationTerms> store = new ConcurrentHashMap<>();

    @Override
    public CompletionStage<CompensationTerms> save(CompensationTerms e) {
        store.put(e.getId(), e);
        return CompletableFuture.completedFuture(e);
    }

    @Override
    public CompletionStage<Optional<CompensationTerms>> findById(CompensationTermsId id) {
        return CompletableFuture.completedFuture(Optional.ofNullable(store.get(id)));
    }

    @Override
    public CompletionStage<Boolean> existsById(CompensationTermsId id) {
        return CompletableFuture.completedFuture(store.containsKey(id));
    }

    @Override
    public CompletionStage<Void> delete(CompensationTerms e) {
        store.remove(e.getId());
        return CompletableFuture.completedFuture(null);
    }

    @Override
    public CompletionStage<Void> deleteById(CompensationTermsId id) {
        store.remove(id);
        return CompletableFuture.completedFuture(null);
    }

    @Override
    public CompletionStage<List<CompensationTerms>> findByEmployment(EmploymentId employmentId) {
        List<CompensationTerms> list = store.values().stream()
                .filter(t -> t.employmentId().equals(employmentId))
                .toList();
        return CompletableFuture.completedFuture(list);
    }

    @Override
    public CompletionStage<Optional<CompensationTerms>> findEffective(EmploymentId employmentId, LocalDate date) {
        Optional<CompensationTerms> opt = store.values().stream()
                .filter(t -> t.employmentId().equals(employmentId) && t.isEffectiveOn(date))
                .findFirst();
        return CompletableFuture.completedFuture(opt);
    }
}
