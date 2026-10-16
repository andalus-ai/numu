package tech.kayys.syirkah.accounting.payables.infrastructure;

import tech.kayys.syirkah.accounting.domain.ap.VendorInvoice;
import tech.kayys.syirkah.accounting.domain.ap.VendorInvoiceId;
import tech.kayys.syirkah.accounting.domain.ap.VendorInvoiceRepository;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/** Thread-safe in-memory repository for tests and bootstrap wiring. */
public final class InMemoryVendorInvoiceRepository implements VendorInvoiceRepository {

    private final Map<VendorInvoiceId, VendorInvoice> store = new ConcurrentHashMap<>();

    public Optional<VendorInvoice> find(VendorInvoiceId id) {
        return Optional.ofNullable(store.get(id));
    }

    public void save(VendorInvoice invoice) {
        Objects.requireNonNull(invoice);
        store.put(invoice.id(), invoice);
    }
}
