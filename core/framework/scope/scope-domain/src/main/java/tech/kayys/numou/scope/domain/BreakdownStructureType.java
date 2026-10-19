package tech.kayys.numou.scope.domain;

import java.util.Locale;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * Extensible type key (for example WBS, PRODUCT, ORGANIZATION, or LOCATION).
 * The scope domain deliberately does not encode construction-only type enums.
 */
public record BreakdownStructureType(String code) {
    private static final Pattern VALID_CODE = Pattern.compile("[A-Z][A-Z0-9_]{0,63}");

    public BreakdownStructureType {
        Objects.requireNonNull(code, "Breakdown structure type cannot be null");
        code = code.trim().toUpperCase(Locale.ROOT);
        if (!VALID_CODE.matcher(code).matches()) {
            throw new IllegalArgumentException(
                    "Breakdown structure type must be 1-64 uppercase letters, digits, or underscores and start with a letter");
        }
    }

    public static BreakdownStructureType of(String code) {
        return new BreakdownStructureType(code);
    }
}
