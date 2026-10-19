package tech.kayys.numou.event.domain.valueobject;

import tech.kayys.numou.foundation.domain.valueobject.ValueObject;

/**
 * Version of an event <em>contract</em>, not of its payload instance.
 *
 * <p>Versioning from day one (base01.md §P1-11) is what lets a consumer
 * ignore a field it does not understand yet, or keep working while a
 * publisher adds one. Additive change increments the minor number; a
 * breaking change increments the major number and requires a new handler.
 */
public record EventVersion(int major, int minor) implements ValueObject {

    public static final EventVersion V1 = new EventVersion(1, 0);

    public EventVersion {
        if (major < 1) {
            throw new IllegalArgumentException("Event contract major version must be >= 1");
        }
        if (minor < 0) {
            throw new IllegalArgumentException("Event contract minor version cannot be negative");
        }
    }

    public static EventVersion of(int major, int minor) {
        return new EventVersion(major, minor);
    }

    /** Next version after an additive, backward-compatible change. */
    public EventVersion nextMinor() {
        return new EventVersion(major, minor + 1);
    }

    /** Next version after a breaking change. */
    public EventVersion nextMajor() {
        return new EventVersion(major + 1, 0);
    }

    public boolean isBackwardCompatibleWith(EventVersion other) {
        return other != null && other.major == this.major && other.minor <= this.minor;
    }

    @Override
    public String toString() {
        return major + "." + minor;
    }
}
