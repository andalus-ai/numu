package tech.kayys.numou.pos.application;

import tech.kayys.numou.pos.application.port.out.OpenShiftAccess;
import tech.kayys.numou.pos.application.port.out.SaleRepository;
import tech.kayys.numou.pos.application.port.out.SaleRepository.VersionedSale;
import tech.kayys.numou.pos.domain.Sale;
import tech.kayys.numou.pos.domain.SaleChannel;
import tech.kayys.numou.pos.domain.SaleLine;
import java.time.Clock;
import java.util.Objects;
import java.util.UUID;

/**
 * Neutral sale lifecycle use cases. Completing a sale changes POS state only;
 * tender, inventory, tax, accounting and receipt effects are separate workflows.
 */
public final class SaleCommandService {
    private final SaleRepository sales;
    private final OpenShiftAccess shifts;
    private final Clock clock;

    public SaleCommandService(SaleRepository sales, OpenShiftAccess shifts, Clock clock) {
        this.sales = Objects.requireNonNull(sales, "sales cannot be null");
        this.shifts = Objects.requireNonNull(shifts, "shifts cannot be null");
        this.clock = Objects.requireNonNull(clock, "clock cannot be null");
    }

    public VersionedSale open(SaleCommandContext context, UUID saleId, UUID branchId,
            UUID registerId, UUID shiftId, SaleChannel channel, String currency) {
        Objects.requireNonNull(context, "context cannot be null");
        Objects.requireNonNull(saleId, "saleId cannot be null");
        Objects.requireNonNull(branchId, "branchId cannot be null");
        Objects.requireNonNull(registerId, "registerId cannot be null");
        Objects.requireNonNull(shiftId, "shiftId cannot be null");
        Objects.requireNonNull(channel, "channel cannot be null");
        shifts.requireOpenShift(context.tenantId(), branchId, registerId, shiftId, context.actorId());
        return sales.create(Sale.open(saleId, context.tenantId(), branchId, registerId, shiftId,
                channel, currency, clock.instant()));
    }

    public VersionedSale addLine(SaleCommandContext context, UUID saleId, long expectedVersion, SaleLine line) {
        VersionedSale current = load(context, saleId);
        return sales.update(current.sale().addLine(line), expectedVersion);
    }

    public VersionedSale complete(SaleCommandContext context, UUID saleId, long expectedVersion) {
        VersionedSale current = load(context, saleId);
        return sales.update(current.sale().complete(clock.instant()), expectedVersion);
    }

    public VersionedSale voidOpenSale(SaleCommandContext context, UUID saleId, long expectedVersion) {
        VersionedSale current = load(context, saleId);
        return sales.update(current.sale().voidSale(), expectedVersion);
    }

    private VersionedSale load(SaleCommandContext context, UUID saleId) {
        Objects.requireNonNull(context, "context cannot be null");
        Objects.requireNonNull(saleId, "saleId cannot be null");
        return sales.find(context.tenantId(), saleId)
                .orElseThrow(() -> new SaleNotFoundException(saleId));
    }

    public static final class SaleNotFoundException extends RuntimeException {
        public SaleNotFoundException(UUID saleId) { super("sale not found: " + saleId); }
    }
}
