package tech.kayys.numou.workforce.domain.development;

/**
 * Categorises the nature of a {@link DevelopmentNeed}.
 */
public enum DevelopmentNeedType {
    /** Worker lacks a required skill. */
    SKILL_GAP,
    /** Worker lacks a required area of knowledge. */
    KNOWLEDGE_GAP,
    /** An existing competency needs to be improved. */
    COMPETENCY_IMPROVEMENT,
    /** Worker needs to obtain a professional certification. */
    CERTIFICATION,
    /** Development towards a leadership role. */
    LEADERSHIP,
    /** Any other development need type. */
    OTHER
}
