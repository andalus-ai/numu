package com.saas.product.unit;

import com.saas.product.core.ProductAggregate;
import com.saas.product.core.model.*;
import com.saas.product.core.pricing.PricingContext;
import com.saas.product.core.pricing.PricingResult;
import com.saas.product.extension.fnb.FnbBehavior;
import com.saas.product.extension.fnb.FnbExtension;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.*;

/**
 * Unit tests for FnbBehavior pricing including:
 *  - Channel-based pricing (dine-in vs takeaway vs delivery)
 *  - Modifier add-ons
 *  - Service charge (dine-in only)
 *  - PPN tax
 */
class FnbBehaviorTest {

    private FnbBehavior behavior;
    private ProductAggregate product;
    private static final String TENANT   = "tenant-warung";
    private static final String CURRENCY = "IDR";

    @BeforeEach
    void setUp() {
        behavior = new FnbBehavior();

        ProductCore core = ProductCore.builder()
                .tenantId(TENANT)
                .sku("NASI-GORENG-001")
                .name("Nasi Goreng Special")
                .type(ProductType.SERVICE)
                .build();

        FnbExtension ext = FnbExtension.builder(
                        Money.of(new BigDecimal("35000"), CURRENCY))  // dine-in
                .takeawayPrice(Money.of(new BigDecimal("32000"), CURRENCY))
                .deliveryPrice(Money.of(new BigDecimal("38000"), CURRENCY))
                .preparationTimeMinutes(15)
                .kitchenStation("wok")
                .dietaryTags(List.of("halal"))
                .modifierGroups(List.of(
                        new FnbExtension.ModifierGroup(
                                "spice", "Spice Level", true, 1, 1,
                                List.of(
                                        new FnbExtension.ModifierOption("spice-mild",   "Mild",    Money.zero(CURRENCY), true),
                                        new FnbExtension.ModifierOption("spice-hot",    "Hot",     Money.zero(CURRENCY), false),
                                        new FnbExtension.ModifierOption("spice-xhot",   "X-Hot",   Money.zero(CURRENCY), false)
                                )
                        ),
                        new FnbExtension.ModifierGroup(
                                "extras", "Add Extras", false, 0, 3,
                                List.of(
                                        new FnbExtension.ModifierOption("ext-egg",    "Extra Egg",    Money.of(new BigDecimal("5000"),  CURRENCY), false),
                                        new FnbExtension.ModifierOption("ext-cheese", "Extra Cheese", Money.of(new BigDecimal("8000"),  CURRENCY), false),
                                        new FnbExtension.ModifierOption("ext-meat",   "Extra Meat",   Money.of(new BigDecimal("15000"), CURRENCY), false)
                                )
                        )
                ))
                .build();

        product = ProductAggregate.create(core, "staff");
        product.activate("staff");
        product.putExtension(ext, "staff");
    }

    @Test
    @DisplayName("Dine-in price includes service charge + PPN")
    void dineInWithServiceChargeAndTax() {
        PricingContext ctx = PricingContext.builder(TENANT)
                .channelId("dine-in")
                .quantity(1)
                .build();

        PricingResult result = behavior.calculatePrice(product, ctx);

        // Base: 35000
        assertThat(result.getBasePrice().getAmount()).isEqualByComparingTo("35000");
        // Service charge 5%: 1750, Tax 11%: ~4042.5
        // Final > base due to surcharges
        assertThat(result.getFinalUnitPrice().getAmount())
                .isGreaterThan(result.getBasePrice().getAmount());
    }

    @Test
    @DisplayName("Takeaway uses takeaway price, no service charge")
    void takeawayPriceUsed() {
        PricingContext ctx = PricingContext.builder(TENANT)
                .channelId("takeaway")
                .quantity(1)
                .build();

        PricingResult result = behavior.calculatePrice(product, ctx);

        // Takeaway base = 32000 (less than dine-in 35000)
        assertThat(result.getBasePrice().getAmount()).isEqualByComparingTo("32000");
    }

    @Test
    @DisplayName("Delivery uses delivery price")
    void deliveryPriceUsed() {
        PricingContext ctx = PricingContext.builder(TENANT)
                .channelId("delivery")
                .quantity(1)
                .build();

        PricingResult result = behavior.calculatePrice(product, ctx);

        assertThat(result.getBasePrice().getAmount()).isEqualByComparingTo("38000");
    }

    @Test
    @DisplayName("Selected modifiers add to total")
    void selectedModifiersAddToTotal() {
        // Select extra egg (5000) + extra cheese (8000) = 13000 extra
        PricingContext ctx = PricingContext.builder(TENANT)
                .channelId("takeaway")
                .quantity(1)
                .hints(Map.of("modifiers", "ext-egg,ext-cheese"))
                .build();

        PricingContext ctxNoModifiers = PricingContext.builder(TENANT)
                .channelId("takeaway")
                .quantity(1)
                .build();

        PricingResult withModifiers    = behavior.calculatePrice(product, ctx);
        PricingResult withoutModifiers = behavior.calculatePrice(product, ctxNoModifiers);

        assertThat(withModifiers.getFinalUnitPrice().getAmount())
                .isGreaterThan(withoutModifiers.getFinalUnitPrice().getAmount());
    }

    @Test
    @DisplayName("Audit line items include modifier names")
    void modifierLineItemsPresent() {
        PricingContext ctx = PricingContext.builder(TENANT)
                .channelId("takeaway")
                .quantity(1)
                .hints(Map.of("modifiers", "ext-egg"))
                .build();

        PricingResult result = behavior.calculatePrice(product, ctx);

        assertThat(result.getLineItems())
                .anyMatch(li -> li.label().contains("Extra Egg"));
    }

    @Test
    @DisplayName("Product not available for disabled channel throws")
    void unavailableChannelThrows() {
        // Rebuild with delivery disabled
        FnbExtension noDelivery = FnbExtension.builder(
                        Money.of(new BigDecimal("35000"), CURRENCY))
                .availableForDelivery(false)
                .build();
        product.putExtension(noDelivery, "staff");

        PricingContext ctx = PricingContext.builder(TENANT)
                .channelId("delivery")
                .quantity(1)
                .build();

        assertThatIllegalStateException()
                .isThrownBy(() -> behavior.calculatePrice(product, ctx))
                .withMessageContaining("delivery");
    }

    @Test
    @DisplayName("Quantity multiplies total correctly")
    void quantityMultipliesTotal() {
        PricingContext ctx = PricingContext.builder(TENANT)
                .channelId("takeaway")
                .quantity(2)
                .build();

        PricingResult result = behavior.calculatePrice(product, ctx);

        assertThat(result.getFinalTotalPrice().getAmount())
                .isEqualByComparingTo(
                        result.getFinalUnitPrice().getAmount().multiply(BigDecimal.valueOf(2)));
    }

    @Test
    @DisplayName("isHalal returns true from dietary tags")
    void halalTagDetected() {
        FnbExtension ext = product.requireExtension("fnb");
        assertThat(ext.isHalal()).isTrue();
        assertThat(ext.isVegan()).isFalse();
    }
}
