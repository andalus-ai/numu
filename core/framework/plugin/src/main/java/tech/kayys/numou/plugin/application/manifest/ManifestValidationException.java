package tech.kayys.numou.plugin.application.manifest;

import java.util.List;

public final class ManifestValidationException extends IllegalArgumentException {
    private final List<String> violations;
    public ManifestValidationException(List<String> violations) {
        super(String.join("; ", violations));
        this.violations = List.copyOf(violations);
    }
    public List<String> violations() { return violations; }
}