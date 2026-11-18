package tech.kayys.numou.content.domain.model;

import java.math.BigDecimal;
import java.util.List;
import java.util.Objects;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/** Immutable field contract for one exact content-type schema version. */
public record ContentFieldDefinition(
        String name, FieldType type, boolean required, boolean nullable,
        Integer minLength, Integer maxLength, BigDecimal minNumber, BigDecimal maxNumber,
        String pattern, List<String> allowedValues) {
    public ContentFieldDefinition {
        if (name == null || !name.matches("[a-z][a-z0-9_]{0,62}"))
            throw new IllegalArgumentException("Field name must be a lowercase identifier");
        Objects.requireNonNull(type, "type");
        if (required && nullable) throw new IllegalArgumentException("A required field cannot be nullable");
        if (minLength != null && minLength < 0) throw new IllegalArgumentException("minLength cannot be negative");
        if (maxLength != null && maxLength < 0) throw new IllegalArgumentException("maxLength cannot be negative");
        if (minLength != null && maxLength != null && minLength > maxLength)
            throw new IllegalArgumentException("minLength cannot exceed maxLength");
        if (minNumber != null && maxNumber != null && minNumber.compareTo(maxNumber) > 0)
            throw new IllegalArgumentException("minNumber cannot exceed maxNumber");
        boolean stringLike = type == FieldType.STRING || type == FieldType.TEXT || type == FieldType.EMAIL || type == FieldType.URL;
        boolean numeric = type == FieldType.INTEGER || type == FieldType.DECIMAL;
        if ((minLength != null || maxLength != null || pattern != null) && !stringLike)
            throw new IllegalArgumentException("Length and pattern constraints require a string-like field");
        if ((minNumber != null || maxNumber != null) && !numeric)
            throw new IllegalArgumentException("Numeric range constraints require an integer or decimal field");
        if (pattern != null) {
            try { Pattern.compile(pattern); }
            catch (PatternSyntaxException e) { throw new IllegalArgumentException("Invalid field pattern", e); }
        }
        allowedValues = allowedValues == null ? List.of() : List.copyOf(allowedValues);
        if (allowedValues.stream().anyMatch(Objects::isNull))
            throw new IllegalArgumentException("Allowed values cannot contain null");
        if (!allowedValues.isEmpty() && type != FieldType.STRING && type != FieldType.TEXT
                && type != FieldType.EMAIL && type != FieldType.URL)
            throw new IllegalArgumentException("Allowed values are supported only for string-like fields");
    }
}
