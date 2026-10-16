package tech.kayys.syirkah.project.domain.risk;

/**
 * Risk lifecycle (P10.10):
 *
 * <pre>
 * IDENTIFIED → ASSESSED → RESPONSE_PLANNED → MONITORED → CLOSED
 *                                    └──────────→ MATERIALIZED → (Issue)
 * </pre>
 *
 * Cancellation is deliberately absent: a risk that no longer applies is
 * CLOSED with a reason in the register, not silently deleted.
 */
public enum RiskStatus {

    IDENTIFIED,

    ASSESSED,

    RESPONSE_PLANNED,

    MONITORED,

    MATERIALIZED,

    CLOSED
}
