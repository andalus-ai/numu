package tech.kayys.syirkah.commerce.promotion.domain;

import tech.kayys.syirkah.foundation.domain.identifier.DomainId;

import java.util.UUID;

/**
 * Versioned promotion identity (product04.md §13).
 *
 * <p>Promotion definitions can change over time, so runtime evaluation must
 * bind to an immutable version rather than the bare promotion id. An order
 * evaluated against v2 must not silently become v3 because an administrator
 * edited the promotion afterward.</p>
 */
public record PromotionVersionId(UUID value) implements DomainId<UUID> {

    public PromotionVersionId {
        if (value == null) {
            throw new IllegalArgumentException("Promotion version id cannot be null");
        }
    }

    public static PromotionVersionId generate() {
        return new PromotionVersionId(UUID.randomUUID());
    }

    public static PromotionVersionId of(UUID value) {
        return new PromotionVersionId(value);
    }

    public static PromotionVersionId of(PromotionId promotionId, UUID version) {
        return new PromotionVersionId(version);
    }
}
