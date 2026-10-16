package tech.kayys.numou.accounting.domain.ap;

import java.util.Optional;

public interface VendorInvoiceRepository {
    Optional<VendorInvoice> find(VendorInvoiceId id);
    void save(VendorInvoice invoice);
}
