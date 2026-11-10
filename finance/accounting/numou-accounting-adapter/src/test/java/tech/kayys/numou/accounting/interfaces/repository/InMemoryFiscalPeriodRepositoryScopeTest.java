package tech.kayys.numou.accounting.interfaces.repository;

import org.junit.jupiter.api.Test;
import tech.kayys.numou.accounting.domain.identifier.FiscalPeriodId;
import tech.kayys.numou.accounting.domain.ledger.LedgerId;
import tech.kayys.numou.accounting.domain.model.FiscalPeriod;
import tech.kayys.numou.accounting.domain.multitenancy.TenantRef;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.*;

class InMemoryFiscalPeriodRepositoryScopeTest {
    private final InMemoryFiscalPeriodRepository repository = new InMemoryFiscalPeriodRepository();
    private final TenantRef tenantA = TenantRef.of("tenant-a");
    private final TenantRef tenantB = TenantRef.of("tenant-b");
    private final LedgerId primary = LedgerId.primary();
    private final LedgerId tax = LedgerId.of("tax");

    @Test
    void sameDatesAreAllowedAcrossTenantsAndLedgersButNotWithinOneScope() {
        save(period(tenantA, primary, 1, "2026-01-01", "2026-12-31"));
        save(period(tenantB, primary, 1, "2026-01-01", "2026-12-31"));
        save(period(tenantA, tax, 1, "2026-01-01", "2026-12-31"));

        assertThrows(IllegalArgumentException.class,
                () -> save(period(tenantA, primary, 2, "2026-12-31", "2027-12-30")));
        assertEquals(1, repository.findAll(tenantA, primary).await().indefinitely().size());
        assertEquals(1, repository.findAll(tenantB, primary).await().indefinitely().size());
        assertEquals(1, repository.findAll(tenantA, tax).await().indefinitely().size());
    }

    @Test
    void scopedLookupCannotResolveAnotherTenantsPeriod() {
        FiscalPeriod period = period(tenantA, primary, 1, "2026-01-01", "2026-12-31");
        save(period);

        assertTrue(repository.findById(tenantA, primary, period.id()).await().indefinitely().isPresent());
        assertTrue(repository.findById(tenantB, primary, period.id()).await().indefinitely().isEmpty());
        assertTrue(repository.findActivePeriod(tenantB, primary, LocalDate.of(2026, 6, 1))
                .await().indefinitely().isEmpty());
        assertTrue(repository.findActivePeriod(tenantA, tax, LocalDate.of(2026, 6, 1))
                .await().indefinitely().isEmpty());
    }

    private void save(FiscalPeriod period) {
        repository.save(period).await().indefinitely();
    }

    private static FiscalPeriod period(TenantRef tenant, LedgerId ledger, int number, String start, String end) {
        return new FiscalPeriod(FiscalPeriodId.generate(), tenant, ledger, number, "FY " + number,
                LocalDate.parse(start), LocalDate.parse(end));
    }
}
