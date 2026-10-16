package tech.kayys.numou.asset.domain.custody;

import tech.kayys.numou.foundation.domain.valueobject.ValueObject;

import java.util.Objects;

/**
 * Who currently has custody of an asset.
 *
 * <p>The party may be an employee, a project or a department; only the
 * reference ({@code partyId}) plus a display snapshot is kept here so the
 * Asset aggregate never depends on HR/Project aggregates (see ASSET-12).</p>
 */
public record AssetCustody(
        String partyId,
        String partyType,
        String partyName
) implements ValueObject {

    public AssetCustody {
        Objects.requireNonNull(partyId, "partyId cannot be null");
        Objects.requireNonNull(partyType, "partyType cannot be null");

        partyId = partyId.trim();
        partyType = partyType.trim();
        partyName = partyName == null ? null : partyName.trim();

        if (partyId.isBlank()) {
            throw new IllegalArgumentException("partyId cannot be blank");
        }
        if (partyType.isBlank()) {
            throw new IllegalArgumentException("partyType cannot be blank");
        }
    }

    public static AssetCustody of(String partyId, String partyType, String partyName) {
        return new AssetCustody(partyId, partyType, partyName);
    }
}
