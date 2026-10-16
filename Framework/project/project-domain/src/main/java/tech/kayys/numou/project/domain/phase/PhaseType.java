package tech.kayys.syirkah.project.domain.phase;

/**
 * Generic classification of a phase.
 *
 * Kept deliberately industry-neutral: specific industries can extend
 * the classification (e.g. through a phase catalogue) without changing
 * the Project bounded context itself.
 */
public enum PhaseType {

    PLANNING,

    DESIGN,

    PROCUREMENT,

    PREPARATION,

    EXECUTION,

    TESTING,

    COMMISSIONING,

    HANDOVER,

    CLOSURE,

    OTHER
}
