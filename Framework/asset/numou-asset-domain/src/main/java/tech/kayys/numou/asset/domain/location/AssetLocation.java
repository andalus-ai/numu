package tech.kayys.syirkah.asset.domain.location;

import tech.kayys.syirkah.foundation.domain.valueobject.ValueObject;

import java.util.Objects;

/**
 * Where an asset currently is.
 *
 * <p>{@code locationId} is a <strong>reference</strong> to an external
 * location master — not an owned Location aggregate (see ASSET-12).</p>
 */
public record AssetLocation(
        String locationId,
        String name
) implements ValueObject {

    public AssetLocation {
        Objects.requireNonNull(locationId, "locationId cannot be null");
        Objects.requireNonNull(name, "name cannot be null");

        locationId = locationId.trim();
        name = name.trim();

        if (locationId.isBlank()) {
            throw new IllegalArgumentException("locationId cannot be blank");
        }
        if (name.isBlank()) {
            throw new IllegalArgumentException("location name cannot be blank");
        }
    }

    public static AssetLocation of(String locationId, String name) {
        return new AssetLocation(locationId, name);
    }
}
