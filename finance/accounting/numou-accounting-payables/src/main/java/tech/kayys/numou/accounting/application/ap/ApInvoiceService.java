package tech.kayys.numou.accounting.application.ap;

import tech.kayys.numou.accounting.domain.ap.*;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** Application service enforcing trusted tenant context and operation authorization. */
public final class ApInvoiceService {
    private final VendorInvoiceRepository repository;
    private final ApRequestContextProvider contextProvider;
    private final ApInvoiceAuthorizationPort authorization;

    public ApInvoiceService(VendorInvoiceRepository repository,
                            ApRequestContextProvider contextProvider,
                            ApInvoiceAuthorizationPort authorization) {
        this.repository = Objects.requireNonNull(repository, "repository");
        this.contextProvider = Objects.requireNonNull(contextProvider, "contextProvider");
        this.authorization = Objects.requireNonNull(authorization, "authorization");
    }

    public VendorInvoice register(VendorInvoiceId id, String companyId, String ledgerId, String periodId,
                                  VendorId vendorId, String vendorRef, String currency,
                                  List<VendorInvoiceLine> lines) {
        ApRequestContext actor = contextProvider.requireContext();
        VendorInvoiceScope scope = new VendorInvoiceScope(actor.tenantId(), companyId, ledgerId, periodId);
        authorization.authorize(actor, scope, ApInvoiceOperation.REGISTER);
        if (repository.find(actor.tenantId(), id).isPresent()) {
            throw new IllegalStateException("VendorInvoice already exists: " + id.value());
        }
        VendorInvoice invoice = new VendorInvoice(id, scope, vendorId, vendorRef, currency, lines);
        repository.save(invoice);
        return invoice;
    }

    public VendorInvoice post(VendorInvoiceId id, String apAccount, String taxAccount) {
        return mutate(id, ApInvoiceOperation.POST, invoice -> invoice.post(apAccount, taxAccount));
    }

    public VendorInvoice cancel(VendorInvoiceId id) {
        return mutate(id, ApInvoiceOperation.CANCEL, VendorInvoice::cancel);
    }

    public Optional<VendorInvoice> find(VendorInvoiceId id) {
        ApRequestContext actor = contextProvider.requireContext();
        Optional<VendorInvoice> found = repository.find(actor.tenantId(), id);
        found.ifPresent(invoice -> authorization.authorize(actor, invoice.scope(), ApInvoiceOperation.READ));
        return found;
    }

    private VendorInvoice mutate(VendorInvoiceId id, ApInvoiceOperation operation,
                                 java.util.function.Consumer<VendorInvoice> transition) {
        ApRequestContext actor = contextProvider.requireContext();
        VendorInvoice invoice = repository.find(actor.tenantId(), id)
                .orElseThrow(() -> new IllegalArgumentException("VendorInvoice not found: " + id.value()));
        authorization.authorize(actor, invoice.scope(), operation);
        transition.accept(invoice);
        repository.save(invoice);
        return invoice;
    }
}
