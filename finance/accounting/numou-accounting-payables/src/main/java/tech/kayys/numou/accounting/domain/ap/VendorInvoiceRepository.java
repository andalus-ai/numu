package tech.kayys.numou.accounting.domain.ap;

import java.util.Optional;

/** All reads and writes are tenant-qualified; there is deliberately no ID-only operation. */
public interface VendorInvoiceRepository {
    Optional<VendorInvoice> find(String tenantId, VendorInvoiceId id);
    void save(VendorInvoice invoice);
}
