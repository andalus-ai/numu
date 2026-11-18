package tech.kayys.numou.pos.adapter.sale;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import tech.kayys.numou.pos.application.port.out.CheckoutSalePort;
import tech.kayys.numou.pos.application.port.out.SaleRepository;
import tech.kayys.numou.pos.domain.Sale;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/** Adapts the POS-owned sale repository to the checkout read boundary. */
@ApplicationScoped
public class SaleRepositoryCheckoutAdapter implements CheckoutSalePort {
    private final SaleRepository sales;

    @Inject
    public SaleRepositoryCheckoutAdapter(SaleRepository sales) {
        this.sales = Objects.requireNonNull(sales, "sales");
    }

    @Override
    public Optional<Sale> find(String tenantId, UUID saleId) {
        if (tenantId == null || tenantId.isBlank()) {
            throw new IllegalArgumentException("tenantId cannot be blank");
        }
        UUID tenant;
        try {
            tenant = UUID.fromString(tenantId.trim());
        } catch (IllegalArgumentException invalidTenant) {
            throw new IllegalArgumentException("tenantId must be a UUID", invalidTenant);
        }
        return sales.find(tenant, Objects.requireNonNull(saleId, "saleId"))
                .map(SaleRepository.VersionedSale::sale);
    }
}
