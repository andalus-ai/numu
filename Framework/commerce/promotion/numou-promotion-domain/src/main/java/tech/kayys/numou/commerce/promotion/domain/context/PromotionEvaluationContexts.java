package tech.kayys.syirkah.commerce.promotion.domain.context;

import tech.kayys.syirkah.commerce.promotion.domain.PromotionContext;
import tech.kayys.syirkah.commerce.promotion.domain.PromotionLine;
import tech.kayys.syirkah.foundation.domain.valueobject.Money;
import tech.kayys.syirkah.foundation.domain.valueobject.Quantity;
import tech.kayys.syirkah.foundation.domain.valueobject.Unit;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Adapters between the v1 {@link PromotionContext} cart model and the
 * product03 {@link PromotionEvaluationContext} snapshot boundary.
 */
public final class PromotionEvaluationContexts {

    private static final Unit PCS = Unit.of("pcs");

    private PromotionEvaluationContexts() {
    }

    public static PromotionEvaluationContext from(PromotionContext cart) {
        return from(cart, Optional.empty());
    }

    public static PromotionEvaluationContext from(
            PromotionContext cart,
            Optional<PromotionLineId> currentLineId
    ) {
        Objects.requireNonNull(cart, "cart cannot be null");
        Objects.requireNonNull(currentLineId, "currentLineId cannot be null");

        List<PromotionLineSnapshot> lines = new ArrayList<>();
        List<PromotionLine> source = cart.lines();
        for (int i = 0; i < source.size(); i++) {
            PromotionLine line = source.get(i);
            lines.add(new PromotionLineSnapshot(
                    PromotionLineId.of(line.lineRef() + "#" + i),
                    Optional.empty(),
                    Optional.empty(),
                    line.lineRef(),
                    Quantity.of(line.quantity(), PCS),
                    line.unitPrice()));
        }

        Money total = cart.total();
        Money shipping = Money.zero(total.currency());
        ChannelId channel = cart.channel() == null
                ? ChannelId.of("UNKNOWN")
                : ChannelId.of(cart.channel());
        Optional<CustomerSnapshot> customer = cart.customerRef() == null
                ? Optional.empty()
                : Optional.of(CustomerSnapshot.of(cart.customerRef()));

        return new DefaultPromotionEvaluationContext(
                cart.at(),
                channel,
                customer,
                new CartSnapshot(total, shipping, total),
                lines,
                currentLineId);
    }

    /**
     * Rebuilds a v1 cart for condition/action types that still take
     * {@link PromotionContext}. Line refs come from {@code offeringRef}.
     */
    public static PromotionContext toPromotionContext(PromotionEvaluationContext context) {
        Objects.requireNonNull(context, "context cannot be null");
        if (context.lines().isEmpty()) {
            throw new IllegalArgumentException("evaluation context must have at least one line");
        }

        List<PromotionLine> lines = context.lines().stream()
                .map(line -> new PromotionLine(
                        line.offeringRef(),
                        line.quantity().value().longValueExact(),
                        line.unitPrice()))
                .toList();

        String customerRef = context.customer()
                .map(c -> c.id().value())
                .orElse(null);
        String channel = context.channel().value();
        return new PromotionContext(lines, customerRef, channel, context.effectiveAt());
    }
}
