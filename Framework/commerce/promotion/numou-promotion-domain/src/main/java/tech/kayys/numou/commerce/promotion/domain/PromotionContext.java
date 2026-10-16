package tech.kayys.syirkah.commerce.promotion.domain;

import tech.kayys.syirkah.foundation.domain.valueobject.Money;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Cart snapshot a promotion is evaluated against — the promotion
 * capability's equivalent of the pricing capability's
 * {@code PricingContext}: everything a rule needs, nothing more.
 *
 * Lines are pre-priced by the pricing capability. All lines must share
 * one currency (enforced while computing the total). Channel and
 * customer are opaque references on purpose.
 */
public record PromotionContext(
        List<PromotionLine> lines,
        String customerRef,
        String channel,
        Instant at
) {

    public PromotionContext {
        Objects.requireNonNull(lines, "lines cannot be null");
        Objects.requireNonNull(at, "at cannot be null");
        if (lines.isEmpty()) {
            throw new IllegalArgumentException("cart must have at least one line");
        }
        lines = List.copyOf(lines);
        if (customerRef != null && customerRef.isBlank()) {
            throw new IllegalArgumentException("customerRef cannot be blank");
        }
        if (channel != null && channel.isBlank()) {
            throw new IllegalArgumentException("channel cannot be blank");
        }
    }

    /** Sum of all line totals; also enforces a single currency. */
    public Money total() {
        Money total = null;
        for (var line : lines) {
            total = total == null ? line.lineTotal() : total.add(line.lineTotal());
        }
        return total;
    }

    public long quantityOf(String lineRef) {
        return lines.stream()
                .filter(line -> line.lineRef().equals(lineRef))
                .mapToLong(PromotionLine::quantity)
                .sum();
    }

    public Optional<PromotionLine> cheapestLine(String lineRef) {
        return lines.stream()
                .filter(line -> line.lineRef().equals(lineRef))
                .min((a, b) -> a.unitPrice().compareTo(b.unitPrice()));
    }
}
