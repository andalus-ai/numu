package tech.kayys.numou.plugin.application.manifest;

import java.util.List;

public record VersionRange(Operator operator, SemanticVersion base) {
    public enum Operator { EXACT, CARET, TILDE }

    public static VersionRange parse(String expression) {
        if (expression == null || expression.isBlank()) throw new IllegalArgumentException("Version range is required");
        char first = expression.charAt(0);
        Operator op = first == '^' ? Operator.CARET : first == '~' ? Operator.TILDE : Operator.EXACT;
        String version = op == Operator.EXACT ? expression : expression.substring(1);
        if (version.isBlank() || version.contains(" ") || version.contains("||"))
            throw new IllegalArgumentException("Unsupported v1alpha1 version range: " + expression);
        return new VersionRange(op, SemanticVersion.parse(version));
    }

    public boolean includes(SemanticVersion candidate) {
        if (operator == Operator.EXACT) return base.compareTo(candidate) == 0;
        if (base.prerelease().isEmpty() && !candidate.prerelease().isEmpty()) return false;
        if (candidate.compareTo(base) < 0) return false;
        SemanticVersion upper;
        if (operator == Operator.TILDE) upper = new SemanticVersion(base.major(), base.minor() + 1, 0, List.of(), null);
        else if (base.major() > 0) upper = new SemanticVersion(base.major() + 1, 0, 0, List.of(), null);
        else if (base.minor() > 0) upper = new SemanticVersion(0, base.minor() + 1, 0, List.of(), null);
        else upper = new SemanticVersion(0, 0, base.patch() + 1, List.of(), null);
        return candidate.compareTo(upper) < 0;
    }
}