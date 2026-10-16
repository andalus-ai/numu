package tech.kayys.numou.workforce.domain.development;

import tech.kayys.numou.workforce.domain.position.PositionId;

/**
 * Value object representing one step (milestone) within a {@link CareerPath}.
 *
 * <p>Steps are ordered by {@code stepNumber}. Each step maps to a position
 * and captures the typical years a worker is expected to spend at that level.</p>
 */
public record CareerPathStep(
        /** Ordinal position of this step in the career path (1-based). */
        int stepNumber,
        /** The position that corresponds to this career step. */
        PositionId positionId,
        /** Human-readable title for this step (may differ from position title). */
        String title,
        /** Detailed description of expectations at this step. */
        String description,
        /** Typical number of years spent at this step before progressing. */
        int typicalYears
) {
    public CareerPathStep {
        if (stepNumber < 1) throw new IllegalArgumentException("stepNumber must be >= 1");
        if (positionId == null) throw new IllegalArgumentException("positionId must not be null");
        if (title == null || title.isBlank()) throw new IllegalArgumentException("title must not be blank");
        if (typicalYears < 0) throw new IllegalArgumentException("typicalYears must not be negative");
    }
}
