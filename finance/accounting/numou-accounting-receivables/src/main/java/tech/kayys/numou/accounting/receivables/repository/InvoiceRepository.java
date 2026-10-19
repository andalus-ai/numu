package tech.kayys.numou.accounting.receivables.repository;

import tech.kayys.numou.accounting.domain.ar.*;
import java.util.Optional;

public interface InvoiceRepository {
    CustomerInvoice save(CustomerInvoice invoice);
    Optional<CustomerInvoice> find(CustomerInvoiceId id);
}
