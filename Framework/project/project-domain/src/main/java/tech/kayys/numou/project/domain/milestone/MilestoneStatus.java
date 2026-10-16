package tech.kayys.syirkah.project.domain.milestone;

/**
 * Lifecycle status of a project milestone.
 *
 * A milestone is a significant point in time, not a period of work:
 * it is either planned, reached, missed or cancelled.
 */
public enum MilestoneStatus {

    PLANNED,

    REACHED,

    MISSED,

    CANCELLED
}
