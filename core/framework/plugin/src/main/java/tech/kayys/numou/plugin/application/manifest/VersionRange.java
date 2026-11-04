package tech.kayys.numou.plugin.application.manifest;

import java.math.BigInteger;
import java.util.List;

/** The deliberately small, documented v1alpha1 range grammar: exact, caret, or tilde. */
public record VersionRange(Operator operator, SemanticVersion base) {
    public enum Operator { EXACT, CARET, TILDE }

    public static VersionRange parse(String expression) {
        if (expression == null || expression.isBlank())
            throw new IllegalArgumentException("Version range is required");
        char first = expression.charAt(0);
        Operator op = first == '^' ? Operator.CARET : first == '~' ? Operator.TILDE : Operator.EXACT;
        String version = op == Operator.EXACT ? expression : expression.substring(1);
        if (version.isBlank() || !version.equals(version.trim()) || version.contains("||"))
            throw new IllegalArgumentException("Unsupported v1alpha1 version range: " + expression);
        return new VersionRange(op, SemanticVersion.parse(version));
    }

    public boolean includes(SemanticVersion candidate) {
        if (operator == Operator.EXACT) return base.compareTo(candidate) == 0;
        if (candidate.compareTo(base) < 0) return false;
        // SemVer range semantics: prereleases are admitted only when the comparator
        // explicitly names a prerelease for the same major/minor/patch tuple.
        if (!candidate.prerelease().isEmpty() &&
                (base.prerelease().isEmpty()
                        || !base.major().equals(candidate.major())
                        || !base.minor().equals(candidate.minor())
                        || !base.patch().equals(candidate.patch()))) return false;

        SemanticVersion upper;
        if (operator == Operator.TILDE) {
            upper = new SemanticVersion(base.major(), base.minor().add(BigInteger.ONE),
                    BigInteger.ZERO, List.of(), null);
        } else if (base.major().signum() > 0) {
            upper = new SemanticVersion(base.major().add(BigInteger.ONE),
                    BigInteger.ZERO, BigInteger.ZERO, List.of(), null);
        } else if (base.minor().signum() > 0) {
            upper = new SemanticVersion(BigInteger.ZERO, base.minor().add(BigInteger.ONE),
                    BigInteger.ZERO, List.of(), null);
        } else {
            upper = new SemanticVersion(BigInteger.ZERO, BigInteger.ZERO,
                    base.patch().add(BigInteger.ONE), List.of(), null);
        }
        return candidate.compareTo(upper) < 0;
    }
}