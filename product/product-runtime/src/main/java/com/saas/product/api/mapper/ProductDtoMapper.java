package com.saas.product.api.mapper;

import com.saas.product.api.dto.ProductDto;
import com.saas.product.core.ProductAggregate;
import com.saas.product.core.model.*;
import com.saas.product.core.pricing.PricingResult;
import com.saas.product.extension.ecommerce.EcommerceExtension;
import com.saas.product.extension.fnb.FnbExtension;
import com.saas.product.extension.subscription.SubscriptionExtension;
import com.saas.product.spi.ProductQuery.ProductPage;
import jakarta.enterprise.context.ApplicationScoped;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Bidirectional mapper: ProductAggregate / PricingResult ↔ DTOs.
 *
 * Lives in the API layer only — domain objects never reference DTOs.
 */
@ApplicationScoped
public class ProductDtoMapper {

    // ── Aggregate → Response ──────────────────────────────────────────────

    public ProductDto.ProductResponse toResponse(ProductAggregate agg) {
        return new ProductDto.ProductResponse(
                agg.getId().getValue(),
                agg.getTenantId(),
                agg.getCore().getSku(),
                agg.getCore().getName(),
                agg.getCore().getDescription(),
                agg.getCore().getType(),
                agg.getCore().getCategoryId(),
                agg.getCore().getBrandId(),
                agg.getCore().getExternalRef(),
                agg.getStatus(),
                agg.getCore().getAttributes(),
                agg.getCore().getLabels(),
                List.copyOf(agg.getExtensions().keySet()),
                agg.getVersion(),
                agg.getCreatedAt(),
                agg.getUpdatedAt(),
                agg.getCreatedBy(),
                agg.getUpdatedBy()
        );
    }

    public ProductDto.PagedProductResponse toPagedResponse(ProductPage page) {
        return new ProductDto.PagedProductResponse(
                page.items().stream().map(this::toResponse).toList(),
                page.totalCount(),
                page.page(),
                page.size(),
                page.totalPages(),
                page.hasNext()
        );
    }

    // ── PricingResult → Response ──────────────────────────────────────────

    public ProductDto.PriceResponse toPriceResponse(String productId,
                                                     String context,
                                                     PricingResult result) {
        return new ProductDto.PriceResponse(
                productId,
                context,
                toMoney(result.getBasePrice()),
                toMoney(result.getDiscountAmount()),
                toMoney(result.getTaxAmount()),
                toMoney(result.getFinalUnitPrice()),
                toMoney(result.getFinalTotalPrice()),
                result.getQuantity(),
                result.getLineItems().stream()
                        .map(li -> new ProductDto.LineItemDto(
                                li.label(), toMoney(li.amount()), li.type()))
                        .toList()
        );
    }

    // ── Create/Update Request → Core ──────────────────────────────────────

    public ProductCore toCoreFromCreate(ProductDto.CreateProductRequest req, String tenantId) {
        return ProductCore.builder()
                .tenantId(tenantId)
                .sku(req.sku())
                .name(req.name())
                .description(req.description())
                .type(req.type())
                .categoryId(req.categoryId())
                .brandId(req.brandId())
                .externalRef(req.externalRef())
                .attributes(req.attributes() != null ? req.attributes() : Map.of())
                .labels(req.labels() != null ? req.labels() : Map.of())
                .build();
    }

    public ProductCore applyUpdate(ProductCore existing,
                                   ProductDto.UpdateProductRequest req) {
        ProductCore.Builder b = existing.toBuilder();
        if (req.name() != null)        b.name(req.name());
        if (req.description() != null) b.description(req.description());
        if (req.categoryId() != null)  b.categoryId(req.categoryId());
        if (req.brandId() != null)     b.brandId(req.brandId());
        if (req.externalRef() != null) b.externalRef(req.externalRef());
        if (req.attributes() != null)  b.attributes(req.attributes());
        if (req.labels() != null)      b.labels(req.labels());
        return b.build();
    }

    // ── Extension Request → Extension ─────────────────────────────────────

    public EcommerceExtension toEcommerceExtension(ProductDto.EcommerceExtensionRequest req) {
        var builder = EcommerceExtension.builder(
                Money.of(req.basePrice(), req.currencyCode()));

        if (req.compareAtPrice() != null)
            builder.compareAtPrice(Money.of(req.compareAtPrice(), req.currencyCode()));
        if (req.costPrice() != null)
            builder.costPrice(Money.of(req.costPrice(), req.currencyCode()));
        if (req.stockQuantity() != null)
            builder.stockQuantity(req.stockQuantity());
        if (req.lowStockThreshold() != null)
            builder.lowStockThreshold(req.lowStockThreshold());
        if (req.trackInventory() != null)
            builder.trackInventory(req.trackInventory());
        if (req.allowBackorder() != null)
            builder.allowBackorder(req.allowBackorder());
        if (req.parentVariantId() != null)
            builder.parentVariantId(req.parentVariantId());
        if (req.variantOptionKeys() != null)
            builder.variantOptionKeys(req.variantOptionKeys());

        if (req.shippingProfile() != null) {
            var sp = req.shippingProfile();
            builder.shippingProfile(new EcommerceExtension.ShippingProfile(
                    sp.weightKg(), sp.lengthCm(), sp.widthCm(), sp.heightCm(),
                    Boolean.TRUE.equals(sp.requiresRefrigeration()),
                    Boolean.TRUE.equals(sp.isDangerous())));
        }

        if (req.tierPrices() != null) {
            builder.tierPrices(req.tierPrices().stream()
                    .map(t -> new EcommerceExtension.TierPrice(
                            t.minQuantity(),
                            Money.of(t.unitPrice(), t.currencyCode())))
                    .toList());
        }

        return builder.build();
    }

    public FnbExtension toFnbExtension(ProductDto.FnbExtensionRequest req) {
        var builder = FnbExtension.builder(
                Money.of(req.dineInPrice(), req.currencyCode()));

        if (req.takeawayPrice() != null)
            builder.takeawayPrice(Money.of(req.takeawayPrice(), req.currencyCode()));
        if (req.deliveryPrice() != null)
            builder.deliveryPrice(Money.of(req.deliveryPrice(), req.currencyCode()));
        if (req.preparationTimeMinutes() != null)
            builder.preparationTimeMinutes(req.preparationTimeMinutes());
        if (req.dietaryTags() != null)
            builder.dietaryTags(req.dietaryTags());
        if (req.allergens() != null)
            builder.allergens(req.allergens());
        if (req.kitchenStation() != null)
            builder.kitchenStation(req.kitchenStation());
        if (req.availableForDineIn() != null)
            builder.availableForDineIn(req.availableForDineIn());
        if (req.availableForTakeaway() != null)
            builder.availableForTakeaway(req.availableForTakeaway());
        if (req.availableForDelivery() != null)
            builder.availableForDelivery(req.availableForDelivery());

        if (req.modifierGroups() != null) {
            builder.modifierGroups(req.modifierGroups().stream()
                    .map(g -> new FnbExtension.ModifierGroup(
                            g.id(), g.name(),
                            Boolean.TRUE.equals(g.required()),
                            g.minSelect() != null ? g.minSelect() : 0,
                            g.maxSelect() != null ? g.maxSelect() : 1,
                            g.options() != null
                                    ? g.options().stream()
                                        .map(o -> new FnbExtension.ModifierOption(
                                                o.id(), o.name(),
                                                Money.of(o.additionalPrice() != null
                                                        ? o.additionalPrice()
                                                        : BigDecimal.ZERO, req.currencyCode()),
                                                Boolean.TRUE.equals(o.isDefault())))
                                        .toList()
                                    : List.of()))
                    .toList());
        }

        return builder.build();
    }

    public SubscriptionExtension toSubscriptionExtension(ProductDto.SubscriptionExtensionRequest req) {
        var builder = SubscriptionExtension.builder(
                Money.of(req.monthlyPrice(), req.currencyCode()));

        if (req.annualPrice() != null)
            builder.annualPrice(Money.of(req.annualPrice(), req.currencyCode()));
        if (req.setupFee() != null)
            builder.setupFee(Money.of(req.setupFee(), req.currencyCode()));
        if (req.trialDays() != null)
            builder.trialDays(req.trialDays());
        if (req.seatBased() != null)
            builder.seatBased(req.seatBased());
        if (req.includedSeats() != null)
            builder.includedSeats(req.includedSeats());
        if (req.pricePerAdditionalSeat() != null)
            builder.pricePerAdditionalSeat(Money.of(req.pricePerAdditionalSeat(), req.currencyCode()));
        if (req.maxSeats() != null)
            builder.maxSeats(req.maxSeats());
        if (req.entitlements() != null)
            builder.entitlements(req.entitlements());
        if (req.defaultInterval() != null)
            builder.defaultInterval(SubscriptionExtension.BillingInterval.valueOf(
                    req.defaultInterval().toUpperCase()));

        return builder.build();
    }

    // ── Internal helpers ──────────────────────────────────────────────────

    private ProductDto.MoneyDto toMoney(Money money) {
        if (money == null) return null;
        return new ProductDto.MoneyDto(money.getAmount(), money.getCurrencyCode());
    }
}
