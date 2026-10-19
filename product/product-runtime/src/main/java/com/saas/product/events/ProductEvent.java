package com.saas.product.events;

import com.saas.product.core.lifecycle.ProductStatus;
import com.saas.product.core.model.ProductId;
import com.saas.product.core.model.ProductType;

import java.time.Instant;

/**
 * All product domain events.
 *
 * Sealed hierarchy ensures exhaustive handling in switch expressions.
 * Each event is an immutable record.
 *
 * Events can be published to:
 *  - CDI event bus (in-process)
 *  - Kafka topics (cross-service)
 *  - Outbox table (transactional guarantee)
 */
public sealed interface ProductEvent
        permits ProductEvent.ProductCreated,
                ProductEvent.ProductActivated,
                ProductEvent.ProductSuspended,
                ProductEvent.ProductArchived,
                ProductEvent.ProductCoreUpdated,
                ProductEvent.ExtensionAdded,
                ProductEvent.ExtensionRemoved,
                ProductEvent.PriceCalculated {

    ProductId productId();
    String tenantId();
    Instant occurredAt();
    String actor();

    // ── Create ──────────────────────────────────────────────────────────

    record ProductCreated(
            ProductId productId,
            String tenantId,
            String sku,
            String name,
            ProductType type,
            String actor,
            Instant occurredAt
    ) implements ProductEvent {}

    // ── Lifecycle ────────────────────────────────────────────────────────

    record ProductActivated(
            ProductId productId,
            String tenantId,
            String actor,
            Instant occurredAt
    ) implements ProductEvent {}

    record ProductSuspended(
            ProductId productId,
            String tenantId,
            String reason,
            String actor,
            Instant occurredAt
    ) implements ProductEvent {}

    record ProductArchived(
            ProductId productId,
            String tenantId,
            String actor,
            Instant occurredAt
    ) implements ProductEvent {}

    // ── Update ───────────────────────────────────────────────────────────

    record ProductCoreUpdated(
            ProductId productId,
            String tenantId,
            String changedField,
            String oldValue,
            String newValue,
            String actor,
            Instant occurredAt
    ) implements ProductEvent {}

    // ── Extension ────────────────────────────────────────────────────────

    record ExtensionAdded(
            ProductId productId,
            String tenantId,
            String context,
            String actor,
            Instant occurredAt
    ) implements ProductEvent {}

    record ExtensionRemoved(
            ProductId productId,
            String tenantId,
            String context,
            String actor,
            Instant occurredAt
    ) implements ProductEvent {}

    // ── Pricing ──────────────────────────────────────────────────────────

    record PriceCalculated(
            ProductId productId,
            String tenantId,
            String context,
            String channelId,
            String customerId,
            String finalPrice,
            String currency,
            int quantity,
            String actor,
            Instant occurredAt
    ) implements ProductEvent {}
}
