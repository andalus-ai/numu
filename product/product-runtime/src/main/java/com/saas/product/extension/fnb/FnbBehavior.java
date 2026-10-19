package com.saas.product.extension.fnb;

import com.saas.product.core.ProductAggregate;
import com.saas.product.core.model.Money;
import com.saas.product.core.pricing.PricingContext;
import com.saas.product.core.pricing.PricingResult;
import com.saas.product.spi.ProductBehavior;
import com.saas.product.spi.ProductValidationException;
import com.saas.product.spi.ProductValidator;
import jakarta.enterprise.context.ApplicationScoped;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * FnB pricing behavior.
 *
 * Channel determines base price:
 *   dine-in, takeaway, delivery → each may have different pricing.
 *
 * Modifier add-on costs are computed separately and added on top.
 * The PricingContext.hints map carries selected modifier option IDs:
 *   hints["modifiers"] = "opt-1,opt-3,opt-7"
 */
@ApplicationScoped
public class FnbBehavior implements ProductBehavior {

    private static final BigDecimal PPN_RATE       = new BigDecimal("0.11");
    private static final BigDecimal SERVICE_RATE   = new BigDecimal("0.05");

    @Override
    public String getContext() { return FnbExtension.CONTEXT; }

    @Override
    public PricingResult calculatePrice(ProductAggregate product, PricingContext ctx) {
        FnbExtension ext   = product.requireExtension(FnbExtension.CONTEXT);
        String channel     = ctx.getChannelId();
        int quantity       = ctx.getQuantity();

        if (!ext.isAvailableFor(channel)) {
            throw new IllegalStateException(
                    "Product " + product.getId() + " is not available for channel: " + channel);
        }

        // Step 1: base price for channel
        Money basePrice = ext.priceForChannel(channel);

        PricingResult.Builder result = PricingResult.builder(basePrice)
                .quantity(quantity)
                .line("Base price (" + channel + ")", basePrice, "BASE");

        // Step 2: modifiers (selected option IDs from hints)
        String modifierHint = ctx.getHints().get("modifiers");
        Money modifierTotal = Money.zero(basePrice.getCurrencyCode());
        if (modifierHint != null && !modifierHint.isBlank()) {
            String[] selectedIds = modifierHint.split(",");
            for (FnbExtension.ModifierGroup modifierGroup : ext.getModifierGroups()) {
                for (FnbExtension.ModifierOption opt : modifierGroup.options()) {
                    for (String selectedId : selectedIds) {
                        if (opt.id().equals(selectedId.trim())) {
                            modifierTotal = modifierTotal.add(opt.additionalPrice());
                            result.line("Modifier: " + opt.name(), opt.additionalPrice(), "FEE");
                        }
                    }
                }
            }
        }

        Money unitTotal = basePrice.add(modifierTotal);

        // Step 3: service charge (dine-in only)
        Money serviceCharge = Money.zero(basePrice.getCurrencyCode());
        if ("dine-in".equals(channel) || "dinein".equals(channel)) {
            serviceCharge = unitTotal.percentage(SERVICE_RATE.multiply(BigDecimal.valueOf(100)));
            result.line("Service charge (5%)", serviceCharge, "FEE");
        }

        // Step 4: PPN (tax) — applied on unit + service charge
        Money taxBase = unitTotal.add(serviceCharge);
        Money tax     = taxBase.percentage(PPN_RATE.multiply(BigDecimal.valueOf(100)));
        result.line("PPN (11%)", tax, "TAX");

        return result
                .discount(Money.zero(basePrice.getCurrencyCode()))
                .tax(tax.add(serviceCharge))  // treat service charge as tax-like surcharge
                .quantity(quantity)
                .build();
    }
}

/**
 * FnB-specific product validation.
 */
@ApplicationScoped
class FnbValidator implements ProductValidator {

    @Override
    public String supports() { return FnbExtension.CONTEXT; }

    @Override
    public void validate(ProductAggregate product) {
        FnbExtension ext = product.requireExtension(FnbExtension.CONTEXT);
        List<String> violations = new ArrayList<>();

        if (ext.getDineInPrice().isZero()) {
            violations.add("fnb: dine-in price must be greater than zero");
        }

        if (ext.getPreparationTimeMinutes() <= 0) {
            violations.add("fnb: preparation time must be positive");
        }

        // Required modifier groups must have at least 2 options
        for (FnbExtension.ModifierGroup group : ext.getModifierGroups()) {
            if (group.required() && group.options().size() < 2) {
                violations.add("fnb: required modifier group '" + group.name()
                        + "' must have at least 2 options");
            }
            if (group.minSelect() > group.maxSelect()) {
                violations.add("fnb: modifier group '" + group.name()
                        + "' minSelect cannot exceed maxSelect");
            }
        }

        if (!violations.isEmpty()) {
            throw new ProductValidationException(violations);
        }
    }
}
