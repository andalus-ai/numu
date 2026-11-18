package tech.kayys.numou.content.domain.model;

import java.util.List;

public record ContentValidationResult(List<Violation> violations) {
    public ContentValidationResult { violations = List.copyOf(violations); }
    public boolean valid() { return violations.isEmpty(); }
    public record Violation(String field, String code, String message) {}
}
