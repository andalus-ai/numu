package tech.kayys.numou.workforce.domain.skill;

/**
 * Lifecycle state of a {@link WorkerSkill}.
 */
public enum WorkerSkillStatus {

    /** The skill is currently active and counted for the worker. */
    ACTIVE,

    /** The skill has been deactivated (e.g., removed or expired). */
    INACTIVE
}
