package tech.kayys.syirkah.asset.domain.valueobject;

/**
 * Coarse classification of an Asset. Intentionally flat: no
 * VehicleAsset extends Asset style inheritance (see ASSET-01).
 */
public enum AssetType {

    VEHICLE,

    MACHINE,

    EQUIPMENT,

    BUILDING,

    PROPERTY,

    IT_EQUIPMENT,

    TOOL,

    FURNITURE,

    DEVICE,

    OTHER
}
