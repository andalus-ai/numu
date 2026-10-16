package tech.kayys.syirkah.accounting.receivables.repository;

import tech.kayys.syirkah.accounting.domain.ar.*;
import java.util.Optional;

public interface InvoiceRepository {
    CustomerInvoice save(CustomerInvoice invoice);
    Optional<CustomerInvoice> find(CustomerInvoiceId id);
}
