package tech.kayys.syirkah.project.domain.risk;

/**
 * Issue lifecycle: OPEN → INVESTIGATING → ACTION_PLANNED → IN_PROGRESS
 * → RESOLVED → CLOSED, with REJECTED as the early exit.
 */
public enum IssueStatus {

    OPEN,

    INVESTIGATING,

    ACTION_PLANNED,

    IN_PROGRESS,

    RESOLVED,

    CLOSED,

    REJECTED
}
