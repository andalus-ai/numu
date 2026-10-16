package tech.kayys.syirkah.project.domain.risk;

/**
 * Response strategy. EXPLOIT and ENHANCE let the same model carry
 * positive risks (opportunities) without a second taxonomy.
 */
public enum RiskResponse {

    AVOID,

    MITIGATE,

    TRANSFER,

    ACCEPT,

    EXPLOIT,

    ENHANCE
}
