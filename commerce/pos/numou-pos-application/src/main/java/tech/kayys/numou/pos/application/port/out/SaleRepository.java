package tech.kayys.numou.pos.application.port.out;

import tech.kayys.numou.pos.domain.Sale;
import java.util.Optional;
import java.util.UUID;

/** Tenant-scoped sale persistence with optimistic concurrency. */
public interface SaleRepository {
    Optional<VersionedSale> find(UUID tenantId, UUID saleId);
    VersionedSale create(Sale sale);
    /** Must atomically compare expectedVersion and reject stale updates. */
    VersionedSale update(Sale sale, long expectedVersion);

    record VersionedSale(Sale sale, long version) {
        public VersionedSale {
            if (sale == null) throw new IllegalArgumentException("sale cannot be null");
            if (version < 0) throw new IllegalArgumentException("version cannot be negative");
        }
    }
}
