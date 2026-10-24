package tech.kayys.numou.courier.domain;

/** Physical grouping used for sortation and custody; independent of a specific warehouse vendor. */
public enum TransportContainerType {
    BAG,
    CAGE,
    PALLET,
    ROLL_CAGE,
    TOTE
}
