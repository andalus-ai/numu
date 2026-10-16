package tech.kayys.syirkah.asset.domain.classification;

import tech.kayys.syirkah.foundation.domain.valueobject.ValueObject;

import java.util.Objects;

/**
 * Business classification of an asset (see ASSET-15).
 *
 * <p>Distinct from {@code AssetType}: the type is structural, whereas the
 * classification is an org-configurable category (with its own master).
 * The id is a reference, the name is a historical snapshot.</p>
 */
public record AssetClassification(
        String classificationId,
        String name
) implements ValueObject {

    public AssetClassification {
        Objects.requireNonNull(classificationId, "classificationId cannot be null");
        Objects.requireNonNull(name, "name cannot be null");

        classificationId = classificationId.trim();
        name = name.trim();

        if (classificationId.isBlank()) {
            throw new IllegalArgumentException("classificationId cannot be blank");
        }
        if (name.isBlank()) {
            throw new IllegalArgumentException("classification name cannot be blank");
        }
    }

    public static AssetClassification of(String classificationId, String name) {
        return new AssetClassification(classificationId, name);
    }
}
