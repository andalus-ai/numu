package tech.kayys.numou.accounting.application.port;

import io.smallrye.mutiny.Uni;
import tech.kayys.numou.accounting.domain.identifier.FiscalPeriodId;
import tech.kayys.numou.accounting.domain.ledger.LedgerId;
import tech.kayys.numou.accounting.domain.model.FiscalPeriod;
import tech.kayys.numou.accounting.domain.multitenancy.TenantRef;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/** Persistence boundary for fiscal periods. Tenant and ledger are mandatory query scope. */
public interface FiscalPeriodRepository {
    Uni<Optional<FiscalPeriod>> findById(TenantRef tenantId, LedgerId ledgerId, FiscalPeriodId id);
    Uni<Optional<FiscalPeriod>> findActivePeriod(TenantRef tenantId, LedgerId ledgerId, LocalDate date);
    Uni<List<FiscalPeriod>> findAll(TenantRef tenantId, LedgerId ledgerId);
    Uni<Void> save(FiscalPeriod period);

    /** @deprecated Use the explicitly scoped overload. */
    @Deprecated(forRemoval = true)
    Uni<Optional<FiscalPeriod>> findById(FiscalPeriodId id);
    /** @deprecated Use the explicitly scoped overload. */
    @Deprecated(forRemoval = true)
    Uni<Optional<FiscalPeriod>> findActivePeriod(LocalDate date);
    /** @deprecated Use the explicitly scoped overload. */
    @Deprecated(forRemoval = true)
    Uni<List<FiscalPeriod>> findAll();
}
