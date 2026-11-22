package tech.kayys.numou.content.domain.editorial;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

/** Immutable approval policy snapshot captured when a revision is submitted. */
public record EditorialReviewPolicy(Mode mode, List<UUID> reviewers, int requiredApprovals) {
    public enum Mode { SINGLE, ALL, SEQUENTIAL }

    public EditorialReviewPolicy {
        Objects.requireNonNull(mode, "mode");
        reviewers = List.copyOf(Objects.requireNonNull(reviewers, "reviewers"));
        if (reviewers.isEmpty()) throw new IllegalArgumentException("At least one reviewer is required");
        if (reviewers.stream().anyMatch(Objects::isNull) || reviewers.stream().distinct().count() != reviewers.size())
            throw new IllegalArgumentException("Reviewers must be non-null and unique");
        if (requiredApprovals < 1 || requiredApprovals > reviewers.size())
            throw new IllegalArgumentException("Required approvals must be within reviewer count");
        if (mode == Mode.SINGLE && requiredApprovals != 1)
            throw new IllegalArgumentException("SINGLE policy requires exactly one approval");
        if (mode == Mode.ALL && requiredApprovals != reviewers.size())
            throw new IllegalArgumentException("ALL policy requires every reviewer");
        if (mode == Mode.SEQUENTIAL && requiredApprovals != reviewers.size())
            throw new IllegalArgumentException("SEQUENTIAL policy requires every reviewer in order");
    }
}
