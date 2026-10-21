package tech.kayys.numou.education.school.adapter.memory;

import java.time.YearMonth;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;
import tech.kayys.numou.education.core.StudentId;
import tech.kayys.numou.education.school.domain.FeeInvoice;
import tech.kayys.numou.education.school.domain.FeeInvoiceId;
import tech.kayys.numou.education.school.spi.FeeInvoiceRepository;

public class InMemoryFeeInvoiceRepository implements FeeInvoiceRepository {
    private final Map<FeeInvoiceId, FeeInvoice> store = new ConcurrentHashMap<>();

    @Override public CompletionStage<FeeInvoice> save(FeeInvoice e) { store.put(e.getId(), e); return CompletableFuture.completedFuture(e); }
    @Override public CompletionStage<Optional<FeeInvoice>> findById(FeeInvoiceId id) { return CompletableFuture.completedFuture(Optional.ofNullable(store.get(id))); }
    @Override public CompletionStage<Boolean> existsById(FeeInvoiceId id) { return CompletableFuture.completedFuture(store.containsKey(id)); }
    @Override public CompletionStage<Void> delete(FeeInvoice e) { store.remove(e.getId()); return CompletableFuture.completedFuture(null); }
    @Override public CompletionStage<Void> deleteById(FeeInvoiceId id) { store.remove(id); return CompletableFuture.completedFuture(null); }
    @Override
    public CompletionStage<Optional<FeeInvoice>> findByStudentAndPeriod(StudentId studentId, YearMonth period) {
        return CompletableFuture.completedFuture(store.values().stream().filter(i -> i.getStudentId().equals(studentId) && i.getPeriod().equals(period) && !i.isVoid()).findFirst());
    }
}
