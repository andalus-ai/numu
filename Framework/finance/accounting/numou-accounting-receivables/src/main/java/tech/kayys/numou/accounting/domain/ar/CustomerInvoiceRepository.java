package tech.kayys.syirkah.accounting.domain.ar;

import java.util.Optional;

public interface CustomerInvoiceRepository {
    Optional<CustomerInvoice> find(CustomerInvoiceId id);
    void save(CustomerInvoice invoice);
}
