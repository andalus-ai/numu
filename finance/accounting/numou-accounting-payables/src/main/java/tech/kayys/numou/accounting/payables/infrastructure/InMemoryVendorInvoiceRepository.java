package tech.kayys.numou.accounting.payables.infrastructure;

import tech.kayys.numou.accounting.domain.ap.VendorInvoice;
import tech.kayys.numou.accounting.domain.ap.VendorInvoiceId;
import tech.kayys.numou.accounting.domain.ap.VendorInvoiceRepository;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/** Tenant-isolated in-memory repository for tests and explicitly non-durable bootstrap use. */
public final class InMemoryVendorInvoiceRepository implements VendorInvoiceRepository {
    private record Key(String tenantId, VendorInvoiceId invoiceId) {
        private Key {
            if (tenantId == null || tenantId.isBlank()) throw new IllegalArgumentException("tenantId must not be blank");
            Objects.requireNonNull(invoiceId, "invoiceId");
        }
    }

    private final Map<Key, VendorInvoice> store = new ConcurrentHashMap<>();

    @Override
    public Optional<VendorInvoice> find(String tenantId, VendorInvoiceId id) {
        return Optional.ofNullable(store.get(new Key(tenantId, id)));
    }

    @Override
    public void save(VendorInvoice invoice) {
        Objects.requireNonNull(invoice, "invoice");
        store.put(new Key(invoice.scope().tenantId(), invoice.id()), invoice);
    }
}
