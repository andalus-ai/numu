package tech.kayys.syirkah.commerce.promotion.domain.capability;

import java.util.Objects;

/**
 * Versioned capability identity (product03.md §69), e.g.
 * {@code percentage_discount@1}.
 *
 * <p>Version travels with the persisted tenant definition so a later capability
 * upgrade cannot silently change the meaning of an existing promotion.</p>
 */
public record PromotionCapabilityId(String type, String version) {

    public PromotionCapabilityId {
        Objects.requireNonNull(type, "type cannot be null");
        Objects.requireNonNull(version, "version cannot be null");
        type = type.trim();
        version = version.trim();
        if (type.isBlank()) {
            throw new IllegalArgumentException("type cannot be blank");
        }
        if (version.isBlank()) {
            throw new IllegalArgumentException("version cannot be blank");
        }
    }

    public static PromotionCapabilityId of(String type, String version) {
        return new PromotionCapabilityId(type, version);
    }

    @Override
    public String toString() {
        return type + "@" + version;
    }
}