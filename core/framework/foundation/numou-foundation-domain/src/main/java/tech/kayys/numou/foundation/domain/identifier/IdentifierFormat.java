package tech.kayys.numou.foundation.domain.identifier;

import java.io.Serializable;
import java.util.Objects;

/**
 * Format specification and renderer for business numbering (config03.md §P4-15 #5).
 */
public record IdentifierFormat(
        String prefix,
        int numericWidth,
        int version
) implements Serializable {

    public IdentifierFormat {
        Objects.requireNonNull(prefix, "prefix cannot be null");

        if (numericWidth < 1 || numericWidth > 18) {
            throw new IllegalArgumentException("numericWidth must be between 1 and 18");
        }

        if (version < 1) {
            throw new IllegalArgumentException("version must be positive");
        }
    }

    public static IdentifierFormat of(String prefix, int numericWidth) {
        return new IdentifierFormat(prefix, numericWidth, 1);
    }

    public String format(int year, long sequence) {
        if (sequence < 1) {
            throw new IllegalArgumentException("sequence must be positive");
        }

        String number = String.format("%0" + numericWidth + "d", sequence);
        return prefix + year + "-" + number;
    }

    public String formatWithoutPeriod(long sequence) {
        if (sequence < 1) {
            throw new IllegalArgumentException("sequence must be positive");
        }

        String number = String.format("%0" + numericWidth + "d", sequence);
        return prefix + "-" + number;
    }
}
