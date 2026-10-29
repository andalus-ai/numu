package tech.kayys.numou.pos.domain;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Neutral POS sale aggregate. It coordinates a sale lifecycle and retains item
 * snapshots, but does not execute payments, reserve/move stock, or post accounting.
 */
public record Sale(
        UUID id,
        UUID tenantId,
        UUID branchId,
        UUID registerId,
        UUID shiftId,
        SaleChannel channel,
        String currency,
        Instant openedAt,
        Status status,
        List<SaleLine> lines,
        Instant completedAt) {

    public enum Status {
        OPEN,
        COMPLETED,
        VOIDED
    }

    public Sale {
        Objects.requireNonNull(id, "sale id cannot be null");
        Objects.requireNonNull(tenantId, "tenant id cannot be null");
        Objects.requireNonNull(branchId, "branch id cannot be null");
        Objects.requireNonNull(registerId, "register id cannot be null");
        Objects.requireNonNull(shiftId, "shift id cannot be null");
        Objects.requireNonNull(channel, "channel cannot be null");
        Objects.requireNonNull(currency, "currency cannot be null");
        Objects.requireNonNull(openedAt, "openedAt cannot be null");
        Objects.requireNonNull(status, "status cannot be null");
        Objects.requireNonNull(lines, "lines cannot be null");
        if (!currency.matches("[A-Z]{3}")) {
            throw new IllegalArgumentException("currency must be a three-letter uppercase code");
        }
        lines = List.copyOf(lines);
        if (lines.stream().anyMatch(Objects::isNull)) {
            throw new IllegalArgumentException("sale lines cannot contain null");
        }
        if (lines.stream().map(SaleLine::id).distinct().count() != lines.size()) {
            throw new IllegalArgumentException("sale line IDs must be unique");
        }
        if (lines.stream().anyMatch(line -> !currency.equals(line.unitPrice().currency()))) {
            throw new IllegalArgumentException("all sale lines must use the sale currency");
        }
        if (status == Status.OPEN && completedAt != null) {
            throw new IllegalArgumentException("open sale cannot have completedAt");
        }
        if (status == Status.COMPLETED) {
            Objects.requireNonNull(completedAt, "completedAt is required for completed sale");
            if (completedAt.isBefore(openedAt)) {
                throw new IllegalArgumentException("completedAt cannot be before openedAt");
            }
            if (lines.isEmpty()) {
                throw new IllegalArgumentException("completed sale must contain at least one line");
            }
        }
        if (status == Status.VOIDED && completedAt != null) {
            throw new IllegalArgumentException("voided sale cannot have completedAt");
        }
    }

    public static Sale open(
            UUID id,
            UUID tenantId,
            UUID branchId,
            UUID registerId,
            UUID shiftId,
            SaleChannel channel,
            String currency,
            Instant openedAt) {
        return new Sale(id, tenantId, branchId, registerId, shiftId, channel,
                currency, openedAt, Status.OPEN, List.of(), null);
    }

    public Sale addLine(SaleLine line) {
        requireOpen();
        Objects.requireNonNull(line, "line cannot be null");
        if (lines.stream().anyMatch(existing -> existing.id().equals(line.id()))) {
            throw new IllegalArgumentException("sale line ID already exists");
        }
        if (!currency.equals(line.unitPrice().currency())) {
            throw new IllegalArgumentException("sale line currency must match sale currency");
        }
        List<SaleLine> updated = new ArrayList<>(lines);
        updated.add(line);
        return new Sale(id, tenantId, branchId, registerId, shiftId, channel,
                currency, openedAt, status, updated, completedAt);
    }

    public Sale complete(Instant completedAt) {
        requireOpen();
        Objects.requireNonNull(completedAt, "completedAt cannot be null");
        if (lines.isEmpty()) {
            throw new IllegalStateException("cannot complete a sale without lines");
        }
        if (completedAt.isBefore(openedAt)) {
            throw new IllegalArgumentException("completedAt cannot be before openedAt");
        }
        return new Sale(id, tenantId, branchId, registerId, shiftId, channel,
                currency, openedAt, Status.COMPLETED, lines, completedAt);
    }

    public Sale voidSale() {
        requireOpen();
        return new Sale(id, tenantId, branchId, registerId, shiftId, channel,
                currency, openedAt, Status.VOIDED, lines, null);
    }

    /** Returns the unrounded line subtotal; tax and discount policy are separate. */
    public Money subtotal() {
        Money total = Money.zero(currency);
        for (SaleLine line : lines) {
            total = total.plus(line.extendedAmount());
        }
        return total;
    }

    private void requireOpen() {
        if (status != Status.OPEN) {
            throw new IllegalStateException("sale is not open");
        }
    }
}
