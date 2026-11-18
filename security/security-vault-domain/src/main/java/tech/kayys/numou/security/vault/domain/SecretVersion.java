package tech.kayys.numou.security.vault.domain;

/** Positive, monotonically increasing version number within a logical secret. */
public record SecretVersion(int value) implements Comparable<SecretVersion> {
    public SecretVersion {
        if (value < 1) throw new IllegalArgumentException("secret version must be positive");
    }
    public static SecretVersion initial() { return new SecretVersion(1); }
    public SecretVersion next() { return new SecretVersion(Math.addExact(value, 1)); }
    @Override public int compareTo(SecretVersion other) { return Integer.compare(value, other.value); }
}
