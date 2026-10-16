package tech.kayys.syirkah.inventory.domain.inventory;

/** Types of inventory stock ledger transitions. */
public enum MovementType {
    RECEIPT, ISSUE, TRANSFER_IN, TRANSFER_OUT, ADJUSTMENT
}
