package tech.kayys.numou.crm.domain.territory;

/**
 * Origin of a territory assignment (how it was assigned).
 */
public enum TerritoryAssignmentOrigin {
    MANUAL,
    RULE_EVALUATION,
    IMPORT,
    API,
    MIGRATION
}