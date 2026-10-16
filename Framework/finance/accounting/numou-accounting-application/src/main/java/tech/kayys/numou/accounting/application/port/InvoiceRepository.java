package tech.kayys.syirkah.accounting.application.port;

import tech.kayys.syirkah.accounting.domain.identifier.CustomerId;
import tech.kayys.syirkah.accounting.domain.identifier.InvoiceId;
import tech.kayys.syirkah.accounting.domain.model.Invoice;
import tech.kayys.syirkah.accounting.domain.valueobject.InvoiceStatus;
import io.smallrye.mutiny.Uni;
import java.util.List;
import java.util.Optional;

public interface InvoiceRepository {
    Uni<Optional<Invoice>> findById(InvoiceId id);
    Uni<List<Invoice>> findByCustomerId(CustomerId customerId);
    Uni<List<Invoice>> findByStatus(InvoiceStatus status);
    Uni<Void> save(Invoice invoice);
}
