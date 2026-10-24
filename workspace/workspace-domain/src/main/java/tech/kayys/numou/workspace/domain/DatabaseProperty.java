package tech.kayys.numou.workspace.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

/** Immutable schema property. Keys are stable identifiers; labels are presentation metadata. */
public record DatabaseProperty(
        UUID id,
        String key,
        String label,
        DatabasePropertyType type,
        boolean required,
        List<String> options,
        UUID relatedTableId) {

    private static final Pattern KEY = Pattern.compile("[a-z][a-z0-9_]{0,62}");
    private static final Set<DatabasePropertyType> OPTION_TYPES =
            Set.of(DatabasePropertyType.SELECT, DatabasePropertyType.MULTI_SELECT);

    public DatabaseProperty {
        Objects.requireNonNull(id, "id");
        key = requiredText(key, "key");
        if (!KEY.matcher(key).matches()) {
            throw new IllegalArgumentException("property key must match [a-z][a-z0-9_]{0,62}");
        }
        label = requiredText(label, "label");
        Objects.requireNonNull(type, "type");
        options = options == null ? List.of() : List.copyOf(options);

        if (OPTION_TYPES.contains(type)) {
            if (options.isEmpty()) throw new IllegalArgumentException(type + " requires options");
            if (options.stream().anyMatch(option -> option == null || option.isBlank())) {
                throw new IllegalArgumentException("property options cannot be blank");
            }
            if (options.stream().distinct().count() != options.size()) {
                throw new IllegalArgumentException("property options must be unique");
            }
        } else if (!options.isEmpty()) {
            throw new IllegalArgumentException("options are only valid for SELECT and MULTI_SELECT");
        }

        if (type == DatabasePropertyType.RELATION && relatedTableId == null) {
            throw new IllegalArgumentException("RELATION requires relatedTableId");
        }
        if (type != DatabasePropertyType.RELATION && relatedTableId != null) {
            throw new IllegalArgumentException("relatedTableId is only valid for RELATION");
        }
    }

    /** Validates one raw API value and returns a canonical immutable representation. */
    public Object validateValue(Object value) {
        if (value == null) {
            if (required) throw new IllegalArgumentException("property '" + key + "' is required");
            return null;
        }
        return switch (type) {
            case TEXT -> {
                if (!(value instanceof String text)) throw invalidType();
                yield text;
            }
            case NUMBER -> {
                if (!(value instanceof Number number)) throw invalidType();
                try {
                    yield new BigDecimal(number.toString());
                } catch (NumberFormatException exception) {
                    throw new IllegalArgumentException("property '" + key + "' must be a finite number", exception);
                }
            }
            case BOOLEAN -> {
                if (!(value instanceof Boolean)) throw invalidType();
                yield value;
            }
            case DATE -> {
                if (value instanceof LocalDate date) yield date;
                if (value instanceof String text) {
                    try { yield LocalDate.parse(text); }
                    catch (RuntimeException exception) { throw invalidType(); }
                }
                throw invalidType();
            }
            case DATE_TIME -> {
                if (value instanceof Instant instant) yield instant;
                if (value instanceof String text) {
                    try { yield Instant.parse(text); }
                    catch (RuntimeException exception) { throw invalidType(); }
                }
                throw invalidType();
            }
            case SELECT -> {
                if (!(value instanceof String option) || !options.contains(option)) {
                    throw new IllegalArgumentException("property '" + key + "' must be one of " + options);
                }
                yield option;
            }
            case MULTI_SELECT -> {
                if (!(value instanceof List<?> items)
                        || items.stream().anyMatch(item -> !(item instanceof String) || !options.contains(item))) {
                    throw new IllegalArgumentException("property '" + key + "' contains an unsupported option");
                }
                List<String> selected = items.stream().map(String.class::cast).distinct().toList();
                yield selected;
            }
            case RELATION -> {
                if (!(value instanceof List<?> items)
                        || items.stream().anyMatch(item -> !(item instanceof UUID))) {
                    throw new IllegalArgumentException("property '" + key + "' must contain record UUIDs");
                }
                yield items.stream().map(UUID.class::cast).distinct().toList();
            }
        };
    }

    private IllegalArgumentException invalidType() {
        return new IllegalArgumentException("property '" + key + "' expects " + type);
    }

    private static String requiredText(String value, String field) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(field + " cannot be blank");
        return value.trim();
    }
}
