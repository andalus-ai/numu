package tech.kayys.numou.content.domain.model;

import java.math.BigDecimal;
import java.net.URI;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/** Deterministic validation of already-decoded content values; parsing/transport belongs to adapters. */
public final class ContentValueValidator {
    public ContentValidationResult validate(ContentTypeSchema schema, Map<String, ?> values) {
        List<ContentValidationResult.Violation> errors = new ArrayList<>();
        if (values == null) {
            errors.add(new ContentValidationResult.Violation("$", "required", "Content values are required"));
            return new ContentValidationResult(errors);
        }
        for (String supplied : values.keySet()) {
            if (schema.fields().stream().noneMatch(f -> f.name().equals(supplied)))
                errors.add(new ContentValidationResult.Violation(supplied, "unknown_field", "Field is not defined by this schema"));
        }
        for (ContentFieldDefinition field : schema.fields()) {
            boolean present = values.containsKey(field.name());
            Object value = values.get(field.name());
            if (!present || value == null) {
                if (field.required()) errors.add(v(field, "required", "A value is required"));
                else if (present && !field.nullable()) errors.add(v(field, "null_not_allowed", "Null is not allowed"));
                continue;
            }
            if (!matchesType(field.type(), value)) {
                errors.add(v(field, "type", "Value does not match " + field.type()));
                continue;
            }
            if (value instanceof String s) {
                if (field.minLength() != null && s.length() < field.minLength())
                    errors.add(v(field, "min_length", "Value is shorter than the minimum length"));
                if (field.maxLength() != null && s.length() > field.maxLength())
                    errors.add(v(field, "max_length", "Value exceeds the maximum length"));
                if (field.pattern() != null && !Pattern.matches(field.pattern(), s))
                    errors.add(v(field, "pattern", "Value does not match the required pattern"));
                if (!field.allowedValues().isEmpty() && !field.allowedValues().contains(s))
                    errors.add(v(field, "allowed_values", "Value is not in the allowed set"));
            }
            if (value instanceof Number n) {
                BigDecimal number = new BigDecimal(n.toString());
                if (field.minNumber() != null && number.compareTo(field.minNumber()) < 0)
                    errors.add(v(field, "min_number", "Value is below the minimum"));
                if (field.maxNumber() != null && number.compareTo(field.maxNumber()) > 0)
                    errors.add(v(field, "max_number", "Value exceeds the maximum"));
            }
        }
        return new ContentValidationResult(errors);
    }

    private boolean matchesType(FieldType type, Object value) {
        return switch (type) {
            case STRING, TEXT -> value instanceof String;
            case EMAIL -> value instanceof String s && s.matches("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$");
            case URL -> validUrl(value);
            case INTEGER -> value instanceof Byte || value instanceof Short || value instanceof Integer || value instanceof Long;
            case DECIMAL -> value instanceof Number;
            case BOOLEAN -> value instanceof Boolean;
            case DATE -> validDate(value);
            case DATETIME -> validDateTime(value);
            case JSON -> value instanceof Map<?, ?> || value instanceof List<?>;
        };
    }
    private boolean validUrl(Object value) {
        if (!(value instanceof String s)) return false;
        try { URI uri = URI.create(s); return ("http".equalsIgnoreCase(uri.getScheme()) || "https".equalsIgnoreCase(uri.getScheme())) && uri.getHost() != null; }
        catch (IllegalArgumentException e) { return false; }
    }
    private boolean validDate(Object value) {
        if (!(value instanceof String s)) return false;
        try { LocalDate.parse(s); return true; } catch (RuntimeException e) { return false; }
    }
    private boolean validDateTime(Object value) {
        if (!(value instanceof String s)) return false;
        try { Instant.parse(s); return true; }
        catch (RuntimeException ignored) { try { OffsetDateTime.parse(s); return true; } catch (RuntimeException e) { return false; } }
    }
    private ContentValidationResult.Violation v(ContentFieldDefinition f, String code, String message) {
        return new ContentValidationResult.Violation(f.name(), code, message);
    }
}
