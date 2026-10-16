package tech.kayys.syirkah.project.domain.commercial;

/**
 * Change order lifecycle.
 *
 * Approve (commercial authority agrees) and apply (the change is
 * actually reflected on the contract) are deliberately separate
 * steps.
 */
public enum ChangeOrderStatus {

    DRAFT,

    APPROVED,

    APPLIED,

    CANCELLED
}