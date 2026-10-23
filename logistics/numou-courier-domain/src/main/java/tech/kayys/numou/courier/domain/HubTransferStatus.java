package tech.kayys.numou.courier.domain;

/** Operational state of a sealed shipment manifest moving between two hubs. */
public enum HubTransferStatus {
    DRAFT, SEALED, DEPARTED, ARRIVED, CANCELLED
}
