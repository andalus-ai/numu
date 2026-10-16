package tech.kayys.syirkah.asset.domain.classification;

import tech.kayys.syirkah.foundation.domain.valueobject.ValueObject;

import java.util.Objects;

/**
 * A single key/value attribute attached to an asset (see ASSET-15).
 *
 * <p>Values are stored as strings with an explicit {@link AssetAttributeType}
 * so the aggregate can validate without depending on a specific schema.</p>
 */
public record AssetAttribute(
        String key,
        String value,
        AssetAttributeType type
) implements ValueObject {

    public AssetAttribute {
        Objects.requireNonNull(key, "key cannot be null");
        Objects.requireNonNull(type, "type cannot be null");

        key = key.trim();
        value = value == null ? null : value.trim();

        if (key.isBlank()) {
            throw new IllegalArgumentException("attribute key cannot be blank");
        }
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("attribute value cannot be blank");
        }
    }

    public static AssetAttribute of(String key, String value, AssetAttributeType type) {
        return new AssetAttribute(key, value, type);
    }
}
