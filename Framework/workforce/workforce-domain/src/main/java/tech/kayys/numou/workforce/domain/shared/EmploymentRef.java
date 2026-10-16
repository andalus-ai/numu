package tech.kayys.numou.workforce.domain.shared;

import tech.kayys.numou.workforce.domain.employment.EmploymentId;
import java.util.Objects;

/**
 * Lightweight reference to an Employment across domain boundaries (C-01 §5).
 */
public record EmploymentRef(EmploymentId employmentId) {
    public EmploymentRef {
        Objects.requireNonNull(employmentId, "employmentId must not be null");
    }
}
