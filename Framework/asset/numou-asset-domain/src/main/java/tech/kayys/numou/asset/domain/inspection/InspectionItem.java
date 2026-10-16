package tech.kayys.syirkah.asset.domain.inspection;

import java.util.Objects;

/**
 * A single inspected component within an {@link AssetInspection}
 * (see ASSET-20 sections 20.12-20.13).
 *
 * <p>Kept inside the inspection aggregate so an inspection completes
 * atomically. An item says "we inspected the brakes"; a
 * {@code finding} says what was wrong.</p>
 */
public record InspectionItem(
        InspectionItemId id,
        String component,
        String description,
        AssetCondition condition,
        InspectionResult result,
        String notes
) {

    public InspectionItem {
        Objects.requireNonNull(id, "id cannot be null");
        Objects.requireNonNull(component, "component cannot be null");
        if (component.isBlank()) {
            throw new IllegalArgumentException("component cannot be blank");
        }
    }

    public static InspectionItem of(String component, String description,
                                    AssetCondition condition, InspectionResult result, String notes) {
        return new InspectionItem(InspectionItemId.generate(), component, description, condition, result, notes);
    }
}
