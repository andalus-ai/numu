package tech.kayys.syirkah.accounting.receivables.infrastructure;

import tech.kayys.syirkah.accounting.domain.ar.CustomerInvoice;
import tech.kayys.syirkah.accounting.domain.ar.CustomerInvoiceId;
import tech.kayys.syirkah.accounting.domain.ar.CustomerInvoiceRepository;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/** Thread-safe in-memory store for CustomerInvoice aggregates. */
public final class InMemoryCustomerInvoiceRepository implements CustomerInvoiceRepository {

    private final Map<CustomerInvoiceId, CustomerInvoice> store = new ConcurrentHashMap<>();

    public Optional<CustomerInvoice> find(CustomerInvoiceId id) { return Optional.ofNullable(store.get(id)); }
    public void save(CustomerInvoice inv) { Objects.requireNonNull(inv); store.put(inv.id(), inv); }
}
