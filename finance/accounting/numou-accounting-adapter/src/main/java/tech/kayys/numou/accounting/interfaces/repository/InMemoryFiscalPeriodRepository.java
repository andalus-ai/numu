package tech.kayys.numou.accounting.interfaces.repository;

import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import tech.kayys.numou.accounting.application.port.FiscalPeriodRepository;
import tech.kayys.numou.accounting.domain.identifier.FiscalPeriodId;
import tech.kayys.numou.accounting.domain.ledger.LedgerId;
import tech.kayys.numou.accounting.domain.model.FiscalPeriod;
import tech.kayys.numou.accounting.domain.multitenancy.TenantRef;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@ApplicationScoped
public class InMemoryFiscalPeriodRepository implements FiscalPeriodRepository {
    private final Map<FiscalPeriodId, FiscalPeriod> store = new ConcurrentHashMap<>();

    @Override
    public Uni<Optional<FiscalPeriod>> findById(TenantRef tenantId, LedgerId ledgerId, FiscalPeriodId id) {
        requireScope(tenantId, ledgerId);
        return Uni.createFrom().item(() -> Optional.ofNullable(store.get(id))
                .filter(period -> belongsTo(period, tenantId, ledgerId)));
    }

    @Override
    public Uni<Optional<FiscalPeriod>> findActivePeriod(TenantRef tenantId, LedgerId ledgerId, LocalDate date) {
        requireScope(tenantId, ledgerId);
        Objects.requireNonNull(date, "date");
        return Uni.createFrom().item(() -> store.values().stream()
                .filter(period -> belongsTo(period, tenantId, ledgerId))
                .filter(period -> period.contains(date))
                .findFirst());
    }

    @Override
    public Uni<List<FiscalPeriod>> findAll(TenantRef tenantId, LedgerId ledgerId) {
        requireScope(tenantId, ledgerId);
        return Uni.createFrom().item(() -> store.values().stream()
                .filter(period -> belongsTo(period, tenantId, ledgerId))
                .sorted(java.util.Comparator.comparing(FiscalPeriod::getStartDate))
                .toList());
    }

    @Override
    public Uni<Void> save(FiscalPeriod period) {
        Objects.requireNonNull(period, "period");
        return Uni.createFrom().deferred(() -> {
            synchronized (store) {
                boolean overlaps = store.values().stream()
                        .filter(existing -> !existing.id().equals(period.id()))
                        .filter(existing -> belongsTo(existing, period.getTenantId(), period.getLedgerId()))
                        .anyMatch(existing -> !period.getEndDate().isBefore(existing.getStartDate())
                                && !period.getStartDate().isAfter(existing.getEndDate()));
                if (overlaps) {
                    return Uni.createFrom().failure(new IllegalArgumentException(
                            "Fiscal period overlaps an existing period in the same tenant and ledger"));
                }
                store.put(period.id(), period);
                return Uni.createFrom().voidItem();
            }
        });
    }

    @Override
    @Deprecated(forRemoval = true)
    public Uni<Optional<FiscalPeriod>> findById(FiscalPeriodId id) {
        return Uni.createFrom().item(Optional.ofNullable(store.get(id)));
    }

    @Override
    @Deprecated(forRemoval = true)
    public Uni<Optional<FiscalPeriod>> findActivePeriod(LocalDate date) {
        return Uni.createFrom().item(store.values().stream().filter(period -> period.contains(date)).findFirst());
    }

    @Override
    @Deprecated(forRemoval = true)
    public Uni<List<FiscalPeriod>> findAll() {
        return Uni.createFrom().item(new ArrayList<>(store.values()));
    }

    private static boolean belongsTo(FiscalPeriod period, TenantRef tenantId, LedgerId ledgerId) {
        return period.getTenantId().equals(tenantId) && period.getLedgerId().equals(ledgerId);
    }

    private static void requireScope(TenantRef tenantId, LedgerId ledgerId) {
        Objects.requireNonNull(tenantId, "tenantId is required");
        Objects.requireNonNull(ledgerId, "ledgerId is required");
    }
}
