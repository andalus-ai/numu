package tech.kayys.numou.transport.application;

import tech.kayys.numou.transport.domain.DepartureInventory;
import tech.kayys.numou.transport.domain.PassengerHold;
import tech.kayys.numou.transport.domain.PassengerHoldStatus;
import tech.kayys.numou.transport.spi.DepartureInventoryRepository;
import tech.kayys.numou.transport.spi.InventoryConcurrencyException;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Objects;
import java.util.UUID;
import java.util.function.BiFunction;

/**
 * Application boundary for passenger inventory commands.
 *
 * <p>Every retry reloads the persisted aggregate and re-evaluates the domain command. A stale
 * candidate is never retried. Repository writes atomically commit the inventory version and
 * the complete hold snapshot; external payment or ticketing side effects are intentionally not
 * performed here.</p>
 */
public final class DepartureBookingService {
    private static final int MAX_ATTEMPTS = 3;

    private final DepartureInventoryRepository inventories;
    private final Clock clock;

    public DepartureBookingService(DepartureInventoryRepository inventories, Clock clock) {
        this.inventories = Objects.requireNonNull(inventories, "inventories");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    /**
     * Creates a capacity hold. The caller supplies an expiry so the policy is explicit and stable
     * across network retries; callers should use the same command and idempotency key on replay.
     */
    public PassengerHold hold(HoldCommand command) {
        Objects.requireNonNull(command, "command");
        String fingerprint = fingerprint(command);
        return execute(command.tenantId(), command.departureId(), (current, now) -> {
            DepartureInventory.HoldResult result = current.hold(UUID.randomUUID(), command.idempotencyKey(),
                    fingerprint, command.fromSequence(), command.toSequence(), command.passengerCount(),
                    command.expiresAt(), now);
            return new Mutation<>(result.inventory(), result.hold());
        });
    }

    /** Confirms an active hold. If it has expired, the EXPIRED transition is persisted first. */
    public PassengerHold confirm(String tenantId, UUID departureId, UUID holdId) {
        return execute(tenantId, departureId, (current, now) -> {
            DepartureInventory updated = current.confirm(holdId, now);
            return new Mutation<>(updated, findHold(updated, holdId));
        });
    }

    /** Releases capacity while preserving the hold's audit record. */
    public PassengerHold release(String tenantId, UUID departureId, UUID holdId) {
        return execute(tenantId, departureId, (current, now) -> {
            DepartureInventory updated = current.release(holdId, now);
            return new Mutation<>(updated, findHold(updated, holdId));
        });
    }

    /**
     * Processes a bounded batch of due holds. Safe for repeated scheduler invocations and for
     * multiple application instances: optimistic conflicts cause a fresh read and re-evaluation.
     */
    public int expireDueHolds(int batchSize) {
        if (batchSize < 1 || batchSize > 10_000) {
            throw new IllegalArgumentException("batchSize must be between 1 and 10000");
        }
        Instant scanTime = clock.instant();
        var due = inventories.findDueInventories(scanTime, batchSize);
        int expired = 0;
        for (DepartureInventoryRepository.InventoryKey key : due) {
            expired += expireOne(key, scanTime);
        }
        return expired;
    }

    private int expireOne(DepartureInventoryRepository.InventoryKey key, Instant now) {
        for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
            DepartureInventory current = requireInventory(key.tenantId(), key.departureId());
            DepartureInventory updated = current.expireDue(now);
            if (updated.version() == current.version()) return 0;
            int transitioned = countExpired(current, updated);
            try {
                inventories.save(updated, current.version());
                return transitioned;
            } catch (InventoryConcurrencyException conflict) {
                if (attempt == MAX_ATTEMPTS) throw conflict;
            }
        }
        throw new IllegalStateException("unreachable expiration retry state");
    }

    private <T> T execute(String tenantId, UUID departureId,
                          BiFunction<DepartureInventory, Instant, Mutation<T>> command) {
        requireTenant(tenantId);
        Objects.requireNonNull(departureId, "departureId");
        Objects.requireNonNull(command, "command");
        for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
            DepartureInventory current = requireInventory(tenantId, departureId);
            Instant now = clock.instant();

            // Persist expiration before applying a command. In particular, a confirmation racing
            // the expiry deadline cannot fail while silently losing the EXPIRED audit transition.
            DepartureInventory expired = current.expireDue(now);
            if (expired.version() != current.version()) {
                try {
                    inventories.save(expired, current.version());
                    continue;
                } catch (InventoryConcurrencyException conflict) {
                    if (attempt == MAX_ATTEMPTS) throw conflict;
                    continue;
                }
            }

            Mutation<T> mutation = command.apply(current, now);
            if (mutation.inventory().version() > current.version()) {
                try {
                    inventories.save(mutation.inventory(), current.version());
                } catch (InventoryConcurrencyException conflict) {
                    if (attempt == MAX_ATTEMPTS) throw conflict;
                    continue;
                }
            }
            return mutation.result();
        }
        throw new IllegalStateException("unreachable booking retry state");
    }

    private DepartureInventory requireInventory(String tenantId, UUID departureId) {
        return inventories.find(tenantId, departureId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "no passenger inventory for tenant " + tenantId + " and departure " + departureId));
    }

    private static PassengerHold findHold(DepartureInventory inventory, UUID holdId) {
        return inventory.holds().stream().filter(hold -> hold.id().equals(holdId)).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("unknown passenger hold: " + holdId));
    }

    private static int countExpired(DepartureInventory before, DepartureInventory after) {
        int count = 0;
        for (PassengerHold hold : after.holds()) {
            if (hold.status() == PassengerHoldStatus.EXPIRED) {
                boolean wasExpired = before.holds().stream().anyMatch(old ->
                        old.id().equals(hold.id()) && old.status() == PassengerHoldStatus.EXPIRED);
                if (!wasExpired) count++;
            }
        }
        return count;
    }

    private static String fingerprint(HoldCommand command) {
        String tenant = command.tenantId();
        String canonical = tenant.length() + ":" + tenant + "|"
                + command.departureId() + "|"
                + command.fromSequence() + "|" + command.toSequence() + "|"
                + command.passengerCount() + "|"
                + command.expiresAt().getEpochSecond() + ":" + command.expiresAt().getNano();
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(canonical.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 is unavailable", impossible);
        }
    }

    private static String requireTenant(String tenantId) {
        Objects.requireNonNull(tenantId, "tenantId");
        String normalized = tenantId.trim();
        if (normalized.isEmpty() || normalized.length() > 128) {
            throw new IllegalArgumentException("tenantId must contain 1-128 non-whitespace characters");
        }
        return normalized;
    }

    public record HoldCommand(String tenantId, UUID departureId, String idempotencyKey,
                              int fromSequence, int toSequence, int passengerCount, Instant expiresAt) {
        public HoldCommand {
            tenantId = requireTenant(tenantId);
            Objects.requireNonNull(departureId, "departureId");
            Objects.requireNonNull(idempotencyKey, "idempotencyKey");
            idempotencyKey = idempotencyKey.trim();
            if (idempotencyKey.isEmpty()) throw new IllegalArgumentException("idempotencyKey must not be blank");
            if (fromSequence < 1 || toSequence <= fromSequence) {
                throw new IllegalArgumentException("segment must satisfy 1 <= fromSequence < toSequence");
            }
            if (passengerCount < 1) throw new IllegalArgumentException("passengerCount must be positive");
            Objects.requireNonNull(expiresAt, "expiresAt");
        }
    }

    private record Mutation<T>(DepartureInventory inventory, T result) {
        private Mutation {
            Objects.requireNonNull(inventory, "inventory");
            Objects.requireNonNull(result, "result");
        }
    }
}
