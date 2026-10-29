package tech.kayys.numou.pos.application;

import org.junit.jupiter.api.Test;
import tech.kayys.numou.pos.application.port.out.OpenShiftAccess;
import tech.kayys.numou.pos.application.port.out.SaleRepository;
import tech.kayys.numou.pos.application.port.out.SaleRepository.VersionedSale;
import tech.kayys.numou.pos.domain.Money;
import tech.kayys.numou.pos.domain.Sale;
import tech.kayys.numou.pos.domain.SaleChannel;
import tech.kayys.numou.pos.domain.SaleLine;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class SaleCommandServiceTest {
    private static final UUID TENANT = UUID.randomUUID();
    private static final UUID ACTOR = UUID.randomUUID();
    private static final UUID BRANCH = UUID.randomUUID();
    private static final UUID REGISTER = UUID.randomUUID();
    private static final UUID SHIFT = UUID.randomUUID();
    private static final Instant NOW = Instant.parse("2026-01-01T10:00:00Z");
    private final InMemorySales sales = new InMemorySales();
    private final OpenShiftAccess shifts = (tenant, branch, register, shift, actor) -> {
        assertEquals(TENANT, tenant);
        assertEquals(BRANCH, branch);
        assertEquals(REGISTER, register);
        assertEquals(SHIFT, shift);
        assertEquals(ACTOR, actor);
    };
    private final SaleCommandService service = new SaleCommandService(sales, shifts,
            Clock.fixed(NOW, ZoneOffset.UTC));
    private final SaleCommandContext context = new SaleCommandContext(TENANT, ACTOR, "test-correlation");

    @Test
    void opensSaleFromTrustedTenantAndChecksShift() {
        VersionedSale opened = service.open(context, UUID.randomUUID(), BRANCH, REGISTER, SHIFT, SaleChannel.POS, "IDR");
        assertEquals(0, opened.version());
        assertEquals(TENANT, opened.sale().tenantId());
        assertEquals(NOW, opened.sale().openedAt());
        assertEquals(Sale.Status.OPEN, opened.sale().status());
    }

    @Test
    void mutatesSaleWithOptimisticVersionChecks() {
        UUID id = UUID.randomUUID();
        service.open(context, id, BRANCH, REGISTER, SHIFT, SaleChannel.POS, "IDR");
        SaleLine line = new SaleLine(UUID.randomUUID(), "item-1", "Coffee", "SKU-1",
                new BigDecimal("1"), "cup", new Money(new BigDecimal("25000"), "IDR"));
        VersionedSale withLine = service.addLine(context, id, 0, line);
        assertEquals(1, withLine.version());
        assertEquals(new BigDecimal("25000"), withLine.sale().subtotal().amount());
        assertThrows(IllegalStateException.class, () -> service.complete(context, id, 0));
        VersionedSale completed = service.complete(context, id, 1);
        assertEquals(Sale.Status.COMPLETED, completed.sale().status());
        assertEquals(2, completed.version());
    }

    @Test
    void tenantScopedLookupDoesNotLeakAnotherTenantsSale() {
        UUID id = UUID.randomUUID();
        service.open(context, id, BRANCH, REGISTER, SHIFT, SaleChannel.POS, "IDR");
        SaleCommandContext otherTenant = new SaleCommandContext(UUID.randomUUID(), ACTOR, "other");
        assertThrows(SaleCommandService.SaleNotFoundException.class,
                () -> service.complete(otherTenant, id, 0));
    }

    @Test
    void cannotCompleteEmptySale() {
        UUID id = UUID.randomUUID();
        service.open(context, id, BRANCH, REGISTER, SHIFT, SaleChannel.POS, "IDR");
        assertThrows(IllegalStateException.class, () -> service.complete(context, id, 0));
    }

    private static final class InMemorySales implements SaleRepository {
        private final Map<UUID, VersionedSale> rows = new HashMap<>();
        public Optional<VersionedSale> find(UUID tenant, UUID id) {
            VersionedSale row = rows.get(id);
            return row != null && row.sale().tenantId().equals(tenant) ? Optional.of(row) : Optional.empty();
        }
        public VersionedSale create(Sale sale) {
            if (rows.containsKey(sale.id())) throw new IllegalStateException("duplicate sale");
            VersionedSale row = new VersionedSale(sale, 0);
            rows.put(sale.id(), row);
            return row;
        }
        public VersionedSale update(Sale sale, long expectedVersion) {
            VersionedSale current = rows.get(sale.id());
            if (current == null || !current.sale().tenantId().equals(sale.tenantId())) {
                throw new SaleCommandService.SaleNotFoundException(sale.id());
            }
            if (current.version() != expectedVersion) throw new IllegalStateException("stale sale version");
            VersionedSale updated = new VersionedSale(sale, current.version() + 1);
            rows.put(sale.id(), updated);
            return updated;
        }
    }
}
