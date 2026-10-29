package tech.kayys.numou.pos.application;

import tech.kayys.numou.pos.application.port.out.OpenShiftAccess;
import tech.kayys.numou.pos.application.port.out.SaleCommandIdempotency;
import tech.kayys.numou.pos.application.port.out.SaleRepository;
import tech.kayys.numou.pos.application.port.out.SaleRepository.VersionedSale;
import tech.kayys.numou.pos.domain.Money;
import tech.kayys.numou.pos.domain.Sale;
import tech.kayys.numou.pos.domain.SaleChannel;
import tech.kayys.numou.pos.domain.SaleLine;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.util.HexFormat;
import java.util.Objects;
import java.util.UUID;

/** Neutral sale lifecycle use cases with an explicit durable idempotency boundary. */
public final class SaleCommandService {
    private final SaleRepository sales;
    private final OpenShiftAccess shifts;
    private final SaleCommandIdempotency idempotency;
    private final Clock clock;

    public SaleCommandService(SaleRepository sales, OpenShiftAccess shifts,
            SaleCommandIdempotency idempotency, Clock clock) {
        this.sales = Objects.requireNonNull(sales, "sales cannot be null");
        this.shifts = Objects.requireNonNull(shifts, "shifts cannot be null");
        this.idempotency = Objects.requireNonNull(idempotency, "idempotency cannot be null");
        this.clock = Objects.requireNonNull(clock, "clock cannot be null");
    }

    public VersionedSale open(SaleCommandContext context, String idempotencyKey,
            UUID saleId, UUID branchId, UUID registerId, UUID shiftId,
            SaleChannel channel, String currency) {
        Objects.requireNonNull(context, "context cannot be null");
        Objects.requireNonNull(saleId, "saleId cannot be null");
        Objects.requireNonNull(branchId, "branchId cannot be null");
        Objects.requireNonNull(registerId, "registerId cannot be null");
        Objects.requireNonNull(shiftId, "shiftId cannot be null");
        Objects.requireNonNull(channel, "channel cannot be null");
        String normalizedCurrency = Objects.requireNonNull(currency, "currency cannot be null").trim();
        String fingerprint = fingerprint("open", saleId.toString(), branchId.toString(),
                registerId.toString(), shiftId.toString(), channel.name(), normalizedCurrency);
        return execute(context, idempotencyKey, "sale.open", fingerprint, () -> {
            shifts.requireOpenShift(context.tenantId(), branchId, registerId, shiftId, context.actorId());
            return sales.create(Sale.open(saleId, context.tenantId(), branchId, registerId, shiftId,
                    channel, normalizedCurrency, clock.instant()));
        });
    }

    public VersionedSale addLine(SaleCommandContext context, String idempotencyKey,
            UUID saleId, long expectedVersion, SaleLine line) {
        Objects.requireNonNull(line, "line cannot be null");
        String fingerprint = fingerprint("add-line", saleId.toString(), Long.toString(expectedVersion),
                line.id().toString(), line.itemReference(), line.displayName(),
                line.sku() == null ? "" : line.sku(),
                line.quantity().stripTrailingZeros().toPlainString(), line.unitOfMeasure(),
                line.unitPrice().amount().stripTrailingZeros().toPlainString(), line.unitPrice().currency());
        return execute(context, idempotencyKey, "sale.add-line", fingerprint, () -> {
            VersionedSale current = load(context, saleId);
            requireOpenShift(context, current.sale());
            requireExpectedVersion(current, expectedVersion);
            return sales.update(current.sale().addLine(line), expectedVersion);
        });
    }

    public VersionedSale complete(SaleCommandContext context, String idempotencyKey,
            UUID saleId, long expectedVersion) {
        String fingerprint = fingerprint("complete", saleId.toString(), Long.toString(expectedVersion));
        return execute(context, idempotencyKey, "sale.complete", fingerprint, () -> {
            VersionedSale current = load(context, saleId);
            requireOpenShift(context, current.sale());
            requireExpectedVersion(current, expectedVersion);
            return sales.update(current.sale().complete(clock.instant()), expectedVersion);
        });
    }

    public VersionedSale voidOpenSale(SaleCommandContext context, String idempotencyKey,
            UUID saleId, long expectedVersion) {
        String fingerprint = fingerprint("void-open", saleId.toString(), Long.toString(expectedVersion));
        return execute(context, idempotencyKey, "sale.void-open", fingerprint, () -> {
            VersionedSale current = load(context, saleId);
            requireOpenShift(context, current.sale());
            requireExpectedVersion(current, expectedVersion);
            return sales.update(current.sale().voidSale(), expectedVersion);
        });
    }

    private VersionedSale execute(SaleCommandContext context, String key, String operation,
            String fingerprint, java.util.function.Supplier<VersionedSale> command) {
        Objects.requireNonNull(context, "context cannot be null");
        Objects.requireNonNull(key, "idempotencyKey cannot be null");
        if (key.isBlank()) throw new IllegalArgumentException("idempotencyKey cannot be blank");
        String normalizedKey = key.trim();
        if (normalizedKey.length() > 200) {
            throw new IllegalArgumentException("idempotencyKey cannot exceed 200 characters");
        }
        return idempotency.execute(context.tenantId(), context.actorId(), normalizedKey,
                operation, fingerprint, command);
    }

    private void requireOpenShift(SaleCommandContext context, Sale sale) {
        shifts.requireOpenShift(context.tenantId(), sale.branchId(), sale.registerId(),
                sale.shiftId(), context.actorId());
    }

    private void requireExpectedVersion(VersionedSale current, long expectedVersion) {
        if (current.version() != expectedVersion) {
            throw new ConcurrentSaleModificationException(current.sale().id(), expectedVersion, current.version());
        }
    }

    private VersionedSale load(SaleCommandContext context, UUID saleId) {
        Objects.requireNonNull(saleId, "saleId cannot be null");
        return sales.find(context.tenantId(), saleId)
                .orElseThrow(() -> new SaleNotFoundException(saleId));
    }

    private static String fingerprint(String... parts) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            for (String part : parts) {
                byte[] bytes = Objects.requireNonNull(part, "fingerprint part cannot be null")
                        .getBytes(StandardCharsets.UTF_8);
                digest.update((byte) (bytes.length >>> 24));
                digest.update((byte) (bytes.length >>> 16));
                digest.update((byte) (bytes.length >>> 8));
                digest.update((byte) bytes.length);
                digest.update(bytes);
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 is required by the Java platform", impossible);
        }
    }

    public static final class SaleNotFoundException extends RuntimeException {
        public SaleNotFoundException(UUID saleId) { super("sale not found: " + saleId); }
    }

    public static final class ConcurrentSaleModificationException extends IllegalStateException {
        public ConcurrentSaleModificationException(UUID saleId, long expected, long actual) {
            super("sale " + saleId + " version conflict: expected " + expected + ", actual " + actual);
        }
    }
}
