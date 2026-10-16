package tech.kayys.syirkah.accounting.application.port;

import io.smallrye.mutiny.Uni;
import tech.kayys.syirkah.accounting.domain.identifier.FiscalPeriodId;
import tech.kayys.syirkah.accounting.domain.model.FiscalPeriod;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface FiscalPeriodRepository {
    Uni<Optional<FiscalPeriod>> findById(FiscalPeriodId id);
    Uni<Optional<FiscalPeriod>> findActivePeriod(LocalDate date);
    Uni<List<FiscalPeriod>> findAll();
    Uni<Void> save(FiscalPeriod period);
}
