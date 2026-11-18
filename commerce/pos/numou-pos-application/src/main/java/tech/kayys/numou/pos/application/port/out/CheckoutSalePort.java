package tech.kayys.numou.pos.application.port.out;

import tech.kayys.numou.pos.domain.Sale;
import java.util.Optional;
import java.util.UUID;

/** Tenant-scoped read contract for the authoritative POS sale aggregate. */
@FunctionalInterface
public interface CheckoutSalePort {
    Optional<Sale> find(String tenantId, UUID saleId);
}
