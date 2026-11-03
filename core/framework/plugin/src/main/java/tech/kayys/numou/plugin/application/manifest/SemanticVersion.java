package tech.kayys.numou.plugin.application.manifest;

import java.math.BigInteger;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public record SemanticVersion(int major, int minor, int patch, List<String> prerelease, String build)
        implements Comparable<SemanticVersion> {
    private static final Pattern PATTERN = Pattern.compile("^(0|[1-9]\\d*)\\.(0|[1-9]\\d*)\\.(0|[1-9]\\d*)(?:-([0-9A-Za-z.-]+))?(?:\\+([0-9A-Za-z.-]+))?$");

    public SemanticVersion { prerelease = List.copyOf(prerelease); }

    public static SemanticVersion parse(String value) {
        if (value == null) throw new IllegalArgumentException("SemVer must not be null");
        Matcher m = PATTERN.matcher(value);
        if (!m.matches()) throw new IllegalArgumentException("Invalid SemVer 2.0.0 version: " + value);
        List<String> pre = m.group(4) == null ? List.of() : List.of(m.group(4).split("\\.", -1));
        for (String id : pre) if (id.isEmpty() || (id.matches("\\d+") && id.length() > 1 && id.startsWith("0")))
            throw new IllegalArgumentException("Invalid SemVer prerelease identifier: " + id);
        if (m.group(5) != null) for (String id : m.group(5).split("\\.", -1))
            if (id.isEmpty()) throw new IllegalArgumentException("Invalid SemVer build identifier");
        try {
            return new SemanticVersion(Integer.parseInt(m.group(1)), Integer.parseInt(m.group(2)),
                    Integer.parseInt(m.group(3)), pre, m.group(5));
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("SemVer numeric component exceeds supported integer range", e);
        }
    }

    @Override public int compareTo(SemanticVersion other) {
        int c = Integer.compare(major, other.major); if (c != 0) return c;
        c = Integer.compare(minor, other.minor); if (c != 0) return c;
        c = Integer.compare(patch, other.patch); if (c != 0) return c;
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