package tech.kayys.syirkah.accounting.interfaces.repository;

import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import tech.kayys.syirkah.accounting.application.port.FiscalPeriodRepository;
import tech.kayys.syirkah.accounting.domain.identifier.FiscalPeriodId;
import tech.kayys.syirkah.accounting.domain.model.FiscalPeriod;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@ApplicationScoped
public class InMemoryFiscalPeriodRepository implements FiscalPeriodRepository {
    private final Map<FiscalPeriodId, FiscalPeriod> store = new ConcurrentHashMap<>();

    @Override
    public Uni<Optional<FiscalPeriod>> findById(FiscalPeriodId id) {
        return Uni.createFrom().item(Optional.ofNullable(store.get(id)));
    }

    @Override
    public Uni<Optional<FiscalPeriod>> findActivePeriod(LocalDate date) {
        return Uni.createFrom().item(
                store.values().stream()
                        .filter(p -> p.contains(date))
                        .findFirst()
        );
    }

    @Override
    public Uni<List<FiscalPeriod>> findAll() {
        return Uni.createFrom().item(new ArrayList<>(store.values()));
    }

    @Override
    public Uni<Void> save(FiscalPeriod period) {
        store.put(period.id(), period);
        return Uni.createFrom().voidItem();
    }
}
