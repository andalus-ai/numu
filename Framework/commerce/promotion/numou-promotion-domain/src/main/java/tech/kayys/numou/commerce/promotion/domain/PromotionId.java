package tech.kayys.syirkah.commerce.promotion.domain;

import tech.kayys.syirkah.foundation.domain.identifier.DomainId;

import java.util.UUID;

public record PromotionId(UUID value) implements DomainId<UUID> {

    public PromotionId {
        if (value == null) {
            throw new IllegalArgumentException("Promotion id cannot be null");
        }
    }

    public static PromotionId generate() {
        return new PromotionId(UUID.randomUUID());
    }

    public static PromotionId of(UUID value) {
        return new PromotionId(value);
    }
}
