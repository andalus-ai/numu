package tech.kayys.numou.accounting.receivables.infrastructure;

import tech.kayys.numou.accounting.domain.ar.*;
import tech.kayys.numou.accounting.receivables.repository.InvoiceRepository;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

public final class InMemoryInvoiceRepository implements InvoiceRepository {
    private final Map<CustomerInvoiceId, CustomerInvoice> invoices = new ConcurrentHashMap<>();
    @Override public CustomerInvoice save(CustomerInvoice invoice) { invoices.put(invoice.id(), invoice); return invoice; }
    @Override public Optional<CustomerInvoice> find(CustomerInvoiceId id) { return Optional.ofNullable(invoices.get(id)); }
}
