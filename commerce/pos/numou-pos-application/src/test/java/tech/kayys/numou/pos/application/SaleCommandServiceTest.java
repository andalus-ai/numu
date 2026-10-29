package tech.kayys.numou.pos.application;

import org.junit.jupiter.api.Test;
import tech.kayys.numou.pos.application.port.out.OpenShiftAccess;
import tech.kayys.numou.pos.application.port.out.SaleCommandIdempotency;
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
import java.util.function.Supplier;
import static org.junit.jupiter.api.Assertions.*;

class SaleCommandServiceTest {
    private static final UUID TENANT = UUID.randomUUID();
    private static final UUID ACTOR = UUID.randomUUID();
    private static final UUID BRANCH = UUID.randomUUID();
    private static final UUID REGISTER = UUID.randomUUID();
    private static final UUID SHIFT = UUID.randomUUID();
    private static final Instant NOW = Instant.parse("2026-01-01T10:00:00Z");
    private final InMemorySales sales = new InMemorySales();
    private final InMemoryIdempotency idempotency = new InMemoryIdempotency();
    private final OpenShiftAccess shifts = (tenant, branch, register, shift, actor) -> {
        assertEquals(TENANT, tenant);
        assertEquals(BRANCH, branch);
        assertEquals(REGISTER, register);
        assertEquals(SHIFT, shift);
        assertEquals(ACTOR, actor);
    };
    private final SaleCommandService service = new SaleCommandService(sales, shifts, idempotency,
            Clock.fixed(NOW, ZoneOffset.UTC));
    private final SaleCommandContext context = new SaleCommandContext(TENANT, ACTOR, "test-correlation");

    @Test
    void opensSaleFromTrustedTenantAndChecksShift() {
        UUID id = UUID.randomUUID();
        VersionedSale opened = service.open(context, "open-" + id, id, BRANCH, REGISTER, SHIFT, SaleChannel.POS, "IDR");
        assertEquals(0, opened.version());
        assertEquals(TENANT, opened.sale().tenantId());
        assertEquals(NOW, opened.sale().openedAt());
        assertEquals(Sale.Status.OPEN, opened.sale().status());
    }

    @Test
    void sameOpenCommandKeyReplaysOriginalResult() {
        UUID id = UUID.randomUUID();
        VersionedSale first = service.open(context, "open-replay", id, BRANCH, REGISTER, SHIFT, SaleChannel.POS, "IDR");
        VersionedSale replay = service.open(context, "open-replay", id, BRANCH, REGISTER, SHIFT, SaleChannel.POS, "IDR");
        assertEquals(first, replay);
        assertEquals(1, sales.createCount);
    }

    @Test
    void reusingKeyForDifferentCommandPayloadIsRejected() {
        UUID id = UUID.randomUUID();
        service.open(context, "same-key", id, BRANCH, REGISTER, SHIFT, SaleChannel.POS, "IDR");
        assertThrows(IllegalStateException.class, () ->
                service.open(context, "same-key", UUID.randomUUID(), BRANCH, REGISTER, SHIFT, SaleChannel.POS, "IDR"));
    }

    @Test
    void mutatesSaleWithOptimisticVersionChecksAndIdempotentReplay() {
        UUID id = UUID.randomUUID();
        service.open(context, "open-" + id, id, BRANCH, REGISTER, SHIFT, SaleChannel.POS, "IDR");
        SaleLine line = new SaleLine(UUID.randomUUID(), "item-1", "Coffee", "SKU-1",
                new BigDecimal("1"), "cup", new Money(new BigDecimal("25000"), "IDR"));
        VersionedSale withLine = service.addLine(context, "line-" + id, id, 0, line);
        assertEquals(1, withLine.version());
        assertEquals(withLine, service.addLine(context, "line-" + id, id, 0, line));
        assertEquals(new BigDecimal("25000"), withLine.sale().subtotal().amount());
        assertThrows(IllegalStateException.class, () -> service.complete(context, "complete-stale", id, 0));
        VersionedSale completed = service.complete(context, "complete-" + id, id, 1);
        assertEquals(Sale.Status.COMPLETED, completed.sale().status());
        assertEquals(2, completed.version());
        assertEquals(completed, service.complete(context, "complete-" + id, id, 1));
    }

    @Test
    void tenantScopedLookupDoesNotLeakAnotherTenantsSale() {
        UUID id = UUID.randomUUID();
        service.open(context, "open-" + id, id, BRANCH, REGISTER, SHIFT, SaleChannel.POS, "IDR");
        SaleCommandContext otherTenant = new SaleCommandContext(UUID.randomUUID(), ACTOR, "other");
        assertThrows(SaleCommandService.SaleNotFoundException.class,
                () -> service.complete(otherTenant, "other-tenant", id, 0));
    }

    @Test
    void cannotCompleteEmptySale() {
        UUID id = UUID.randomUUID();
        service.open(context, "open-" + id, id, BRANCH, REGISTER, SHIFT, SaleChannel.POS, "IDR");
        assertThrows(IllegalStateException.class, () -> service.complete(context, "complete-" + id, id, 0));
    }

    private static final class InMemorySales implements SaleRepository {
        private final Map<UUID, VersionedSale> rows = new HashMap<>();
        private int createCount;
        public Optional<VersionedSale> find(UUID tenant, UUID id) {
            VersionedSale row = rows.get(id);
            return row != null && row.sale().tenantId().equals(tenant) ? Optional.of(row) : Optional.empty();
        }
        public VersionedSale create(Sale sale) {
            if (rows.containsKey(sale.id())) throw new IllegalStateException("duplicate sale");
            VersionedSale row = new VersionedSale(sale, 0);
            rows.put(sale.id(), row);
            createCount++;
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

    private static final class InMemoryIdempotency implements SaleCommandIdempotency {
        private final Map<String, Entry> entries = new HashMap<>();
        public VersionedSale execute(UUID tenant, UUID actor, String key, String operation,
                String fingerprint, Supplier<VersionedSale> command) {
            String scopedKey = tenant + ":" + actor + ":" + key;
            Entry existing = entries.get(scopedKey);
            if (existing != null) {
                if (!existing.operation.equals(operation) || !existing.fingerprint.equals(fingerprint)) {
                    throw new IllegalStateException("idempotency key reused with different request");
                }
                return existing.result;
            }
            VersionedSale result = command.get();
            entries.put(scopedKey, new Entry(operation, fingerprint, result));
            return result;
        }
        private record Entry(String operation, String fingerprint, VersionedSale result) {}
    }
}
