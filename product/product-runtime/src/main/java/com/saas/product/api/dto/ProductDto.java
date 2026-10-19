package com.saas.product.api.dto;

import com.saas.product.core.lifecycle.ProductStatus;
import com.saas.product.core.model.ProductType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * All product-related DTOs in one file.
 *
 * Request DTOs use bean validation annotations.
 * Response DTOs are plain records — no validation needed.
 *
 * No domain objects leak outside this package.
 */
public final class ProductDto {

    private ProductDto() {}

    // ── Create Request ────────────────────────────────────────────────────

    public record CreateProductRequest(
            @NotBlank @Size(max = 128) String sku,
            @NotBlank @Size(max = 512) String name,
            @Size(max = 4000) String description,
            @NotNull ProductType type,
            String categoryId,
            String brandId,
            String externalRef,
            Map<String, String> attributes,
            Map<String, String> labels
    ) {}

    // ── Update Request ────────────────────────────────────────────────────

    public record UpdateProductRequest(
            @Size(max = 512) String name,
            @Size(max = 4000) String description,
            String categoryId,
            String brandId,
            String externalRef,
            Map<String, String> attributes,
            Map<String, String> labels
    ) {}

    // ── Extension Requests ────────────────────────────────────────────────

    public record EcommerceExtensionRequest(
            @NotNull BigDecimal basePrice,
            @NotNull String currencyCode,
            BigDecimal compareAtPrice,
            BigDecimal costPrice,
            Integer stockQuantity,
            Integer lowStockThreshold,
            Boolean trackInventory,
            Boolean allowBackorder,
            ShippingProfileDto shippingProfile,
            List<TierPriceDto> tierPrices,
            String parentVariantId,
            List<String> variantOptionKeys
    ) {}

    public record FnbExtensionRequest(
            @NotNull BigDecimal dineInPrice,
            @NotNull String currencyCode,
            BigDecimal takeawayPrice,
            BigDecimal deliveryPrice,
            List<ModifierGroupDto> modifierGroups,
            Integer preparationTimeMinutes,
            List<String> dietaryTags,
            List<String> allergens,
            String kitchenStation,
            Boolean availableForDineIn,
            Boolean availableForTakeaway,
            Boolean availableForDelivery
    ) {}

    public record SubscriptionExtensionRequest(
            @NotNull BigDecimal monthlyPrice,
            @NotNull String currencyCode,
            BigDecimal annualPrice,
            BigDecimal setupFee,
            String defaultInterval,
            Integer trialDays,
            Boolean seatBased,
            Integer includedSeats,
            BigDecimal pricePerAdditionalSeat,
            Integer maxSeats,
            List<String> entitlements
    ) {}

    // ── Price Request ─────────────────────────────────────────────────────

    public record PriceRequest(
            @NotBlank String context,
            @NotBlank String channelId,
            String customerId,
            String customerSegment,
            int quantity,
            List<String> couponCodes,
            Map<String, String> hints
    ) {}

    // ── Response Types ────────────────────────────────────────────────────

    public record ProductResponse(
            String id,
            String tenantId,
            String sku,
            String name,
            String description,
            ProductType type,
            String categoryId,
            String brandId,
            String externalRef,
            ProductStatus status,
            Map<String, String> attributes,
            Map<String, String> labels,
            List<String> extensionContexts,
            long version,
            Instant createdAt,
            Instant updatedAt,
            String createdBy,
            String updatedBy
    ) {}

    public record PriceResponse(
            String productId,
            String context,
            MoneyDto basePrice,
            MoneyDto discountAmount,
            MoneyDto taxAmount,
            MoneyDto finalUnitPrice,
            MoneyDto finalTotalPrice,
            int quantity,
            List<LineItemDto> lineItems
    ) {}

    public record PagedProductResponse(
            List<ProductResponse> items,
            long totalCount,
            int page,
            int size,
            int totalPages,
            boolean hasNext
    ) {}

    // ── Nested / Shared DTOs ──────────────────────────────────────────────

    public record MoneyDto(BigDecimal amount, String currencyCode) {}

    public record LineItemDto(String label, MoneyDto amount, String type) {}

    public record ShippingProfileDto(
            BigDecimal weightKg,
            BigDecimal lengthCm,
            BigDecimal widthCm,
            BigDecimal heightCm,
            Boolean requiresRefrigeration,
            Boolean isDangerous
    ) {}

    public record TierPriceDto(int minQuantity, BigDecimal unitPrice, String currencyCode) {}

    public record ModifierGroupDto(
            String id,
            String name,
            Boolean required,
            Integer minSelect,
            Integer maxSelect,
            List<ModifierOptionDto> options
    ) {}

    public record ModifierOptionDto(
            String id,
            String name,
            BigDecimal additionalPrice,
            String currencyCode,
            Boolean isDefault
    ) {}
}
