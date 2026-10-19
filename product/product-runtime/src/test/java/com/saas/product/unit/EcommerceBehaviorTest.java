package com.saas.product.unit;

import com.saas.product.core.ProductAggregate;
import com.saas.product.core.model.*;
import com.saas.product.core.pricing.PricingContext;
import com.saas.product.core.pricing.PricingResult;
import com.saas.product.extension.ecommerce.EcommerceBehavior;
import com.saas.product.extension.ecommerce.EcommerceExtension;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.*;

/**
 * Unit tests for EcommerceBehavior pricing logic.
 */
class EcommerceBehaviorTest {

    private EcommerceBehavior behavior;
    private ProductAggregate product;
    private static final String TENANT = "tenant-test";
    private static final String CURRENCY = "IDR";

    @BeforeEach
    void setUp() {
        behavior = new EcommerceBehavior();

        ProductCore core = ProductCore.builder()
                .tenantId(TENANT)
                .sku("SHIRT-001")
                .name("Test Shirt")
                .type(ProductType.PHYSICAL)
                .build();

        EcommerceExtension ext = EcommerceExtension.builder(
                        Money.of(new BigDecimal("100000"), CURRENCY))
                .stockQuantity(50)
                .tierPrices(List.of(
                        new EcommerceExtension.TierPrice(10, Money.of(new BigDecimal("90000"), CURRENCY)),
                        new EcommerceExtension.TierPrice(50, Money.of(new BigDecimal("80000"), CURRENCY))
                ))
                .build();

        product = ProductAggregate.create(core, "admin");
        product.activate("admin");
        product.putExtension(ext, "admin");
    }

    @Test
    @DisplayName("Retail customer pays base price + 11% tax")
    void retailPriceWithTax() {
        PricingContext ctx = PricingContext.builder(TENANT)
                .channelId("web")
                .customerSegment("retail")
                .quantity(1)
                .build();

        PricingResult result = behavior.calculatePrice(product, ctx);

        assertThat(result.getBasePrice().getAmount())
                .isEqualByComparingTo("100000");
        assertThat(result.getDiscountAmount().getAmount())
                .isEqualByComparingTo("0");
        // 100000 * 11% = 11000
        assertThat(result.getTaxAmount().getAmount())
                .isEqualByComparingTo("11000");
        // 100000 - 0 + 11000 = 111000
        assertThat(result.getFinalUnitPrice().getAmount())
                .isEqualByComparingTo("111000");
    }

    @Test
    @DisplayName("VIP customer gets 10% discount")
    void vipDiscount() {
        PricingContext ctx = PricingContext.builder(TENANT)
                .channelId("web")
                .customerSegment("vip")
                .quantity(1)
                .build();

        PricingResult result = behavior.calculatePrice(product, ctx);

        // 10% discount on 100000 = 10000
        assertThat(result.getDiscountAmount().getAmount())
                .isEqualByComparingTo("10000");
        assertThat(result.hasDiscount()).isTrue();
    }

    @Test
    @DisplayName("Wholesale customer gets 20% discount")
    void wholesaleDiscount() {
        PricingContext ctx = PricingContext.builder(TENANT)
                .channelId("web")
                .customerSegment("wholesale")
                .quantity(1)
                .build();

        PricingResult result = behavior.calculatePrice(product, ctx);

        // 20% discount on 100000 = 20000
        assertThat(result.getDiscountAmount().getAmount())
                .isEqualByComparingTo("20000");
    }

    @Test
    @DisplayName("Tier pricing applies for quantity >= 10")
    void tierPricingAt10() {
        PricingContext ctx = PricingContext.builder(TENANT)
                .channelId("web")
                .customerSegment("retail")
                .quantity(10)
                .build();

        PricingResult result = behavior.calculatePrice(product, ctx);

        // Tier price at qty 10 = 90000 per unit
        assertThat(result.getBasePrice().getAmount())
                .isEqualByComparingTo("90000");
    }

    @Test
    @DisplayName("Tier pricing selects best tier for quantity 50")
    void tierPricingAt50() {
        PricingContext ctx = PricingContext.builder(TENANT)
                .channelId("web")
                .customerSegment("retail")
                .quantity(50)
                .build();

        PricingResult result = behavior.calculatePrice(product, ctx);

        // Best tier for qty 50 = 80000
        assertThat(result.getBasePrice().getAmount())
                .isEqualByComparingTo("80000");
    }

    @Test
    @DisplayName("Total price = finalUnit × quantity")
    void totalPriceMultipliedByQty() {
        PricingContext ctx = PricingContext.builder(TENANT)
                .channelId("web")
                .customerSegment("retail")
                .quantity(3)
                .build();

        PricingResult result = behavior.calculatePrice(product, ctx);

        assertThat(result.getQuantity()).isEqualTo(3);
        assertThat(result.getFinalTotalPrice().getAmount())
                .isEqualByComparingTo(
                        result.getFinalUnitPrice().getAmount().multiply(BigDecimal.valueOf(3)));
    }

    @Test
    @DisplayName("PricingResult contains audit line items")
    void auditLineItems() {
        PricingContext ctx = PricingContext.builder(TENANT)
                .channelId("web")
                .customerSegment("retail")
                .quantity(1)
                .build();

        PricingResult result = behavior.calculatePrice(product, ctx);

        assertThat(result.getLineItems()).isNotEmpty();
        assertThat(result.getLineItems())
                .anyMatch(li -> li.type().equals("BASE"))
                .anyMatch(li -> li.type().equals("TAX"));
    }

    @Test
    @DisplayName("Cannot calculate price for non-orderable product")
    void cannotPriceNonOrderableProduct() {
        // Product is in ACTIVE state, so we suspend it first
        product.suspend("admin");

        PricingContext ctx = PricingContext.builder(TENANT)
                .channelId("web")
                .quantity(1)
                .build();

        assertThatIllegalStateException()
                .isThrownBy(() -> behavior.assertOrderable(product));
    }
}
