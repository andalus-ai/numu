package tech.kayys.syirkah.asset.domain.movement;

/**
 * The kind of historical fact recorded about an asset (see ASSET-13).
 *
 * <p>Derived from the ASSET-12 domain events; ASSET-13 turns those facts
 * into an append-only movement history.</p>
 */
public enum AssetMovementType {

    LOCATION_CHANGED,

    LOCATION_CLEARED,

    ASSIGNED,

    UNASSIGNED,

    CLASSIFIED,

    CLASSIFICATION_CLEARED,

    RELATIONSHIP_ADDED,

    RELATIONSHIP_REMOVED
}
