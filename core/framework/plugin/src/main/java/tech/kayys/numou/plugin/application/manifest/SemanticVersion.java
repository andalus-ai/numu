package tech.kayys.numou.plugin.application.manifest;

import java.math.BigInteger;
import java.util.List;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** SemVer 2.0.0 value with unbounded numeric components and specification precedence. */
public record SemanticVersion(BigInteger major, BigInteger minor, BigInteger patch,
                              List<String> prerelease, String build) implements Comparable<SemanticVersion> {
    private static final Pattern PATTERN = Pattern.compile(
            "^(0|[1-9]\\d*)\\.(0|[1-9]\\d*)\\.(0|[1-9]\\d*)(?:-([0-9A-Za-z.-]+))?(?:\\+([0-9A-Za-z.-]+))?$");

    public SemanticVersion {
        Objects.requireNonNull(major, "major");
        Objects.requireNonNull(minor, "minor");
        Objects.requireNonNull(patch, "patch");
        if (major.signum() < 0 || minor.signum() < 0 || patch.signum() < 0)
            throw new IllegalArgumentException("SemVer numeric components must be non-negative");
        prerelease = List.copyOf(Objects.requireNonNull(prerelease, "prerelease"));
    }

    public static SemanticVersion parse(String value) {
        if (value == null) throw new IllegalArgumentException("SemVer must not be null");
        Matcher m = PATTERN.matcher(value);
        if (!m.matches()) throw new IllegalArgumentException("Invalid SemVer 2.0.0 version: " + value);
        List<String> pre = m.group(4) == null ? List.of() : List.of(m.group(4).split("\\.", -1));
        for (String id : pre) {
            if (id.isEmpty() || (id.matches("\\d+") && id.length() > 1 && id.startsWith("0")))
                throw new IllegalArgumentException("Invalid SemVer prerelease identifier: " + id);
        }
        if (m.group(5) != null) for (String id : m.group(5).split("\\.", -1))
            if (id.isEmpty()) throw new IllegalArgumentException("Invalid SemVer build identifier");
        return new SemanticVersion(new BigInteger(m.group(1)), new BigInteger(m.group(2)),
                new BigInteger(m.group(3)), pre, m.group(5));
    }

    @Override public int compareTo(SemanticVersion other) {
        int c = major.compareTo(other.major); if (c != 0) return c;
        c = minor.compareTo(other.minor); if (c != 0) return c;
        c = patch.compareTo(other.patch); if (c != 0) return c;
        if (prerelease.isEmpty()) return other.prerelease.isEmpty() ? 0 : 1;
        if (other.prerelease.isEmpty()) return -1;
        for (int i = 0; i < Math.min(prerelease.size(), other.prerelease.size()); i++) {
            String a = prerelease.get(i), b = other.prerelease.get(i);
            boolean an = a.matches("\\d+"), bn = b.matches("\\d+");
            if (an && bn) c = new BigInteger(a).compareTo(new BigInteger(b));
            else if (an != bn) c = an ? -1 : 1;
            else c = a.compareTo(b);
            if (c != 0) return c;
        }
        return Integer.compare(prerelease.size(), other.prerelease.size());
    }
}