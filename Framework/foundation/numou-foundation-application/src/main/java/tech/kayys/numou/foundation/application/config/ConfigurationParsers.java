package tech.kayys.numou.foundation.application.config;

import java.net.URI;
import java.time.Duration;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Type parsers for strongly typed configuration conversion (config01.md §P4-02 #4, #16).
 */
public final class ConfigurationParsers {

    private static final Pattern DURATION_PATTERN =
            Pattern.compile("(?i)^(\\d+)\\s*(ms|s|m|h|d)?$");

    private ConfigurationParsers() {
    }

    @SuppressWarnings("unchecked")
    public static <T> T parse(String rawValue, Class<T> targetType) {
        if (rawValue == null) {
            return null;
        }
        var trimmed = rawValue.trim();

        if (targetType.equals(String.class)) {
            return (T) trimmed;
        }
        if (targetType.equals(Boolean.class) || targetType.equals(boolean.class)) {
            return (T) Boolean.valueOf(parseBoolean(trimmed));
        }
        if (targetType.equals(Integer.class) || targetType.equals(int.class)) {
            return (T) Integer.valueOf(Integer.parseInt(trimmed));
        }
        if (targetType.equals(Long.class) || targetType.equals(long.class)) {
            return (T) Long.valueOf(Long.parseLong(trimmed));
        }
        if (targetType.equals(Double.class) || targetType.equals(double.class)) {
            return (T) Double.valueOf(Double.parseDouble(trimmed));
        }
        if (targetType.equals(Duration.class)) {
            return (T) parseDuration(trimmed);
        }
        if (targetType.equals(UUID.class)) {
            return (T) UUID.fromString(trimmed);
        }
        if (targetType.equals(URI.class)) {
            return (T) URI.create(trimmed);
        }
        if (targetType.isEnum()) {
            return (T) parseEnum(trimmed, (Class<Enum>) targetType);
        }

        throw new IllegalArgumentException("Unsupported configuration target type: " + targetType.getName());
    }

    public static boolean parseBoolean(String value) {
        if ("true".equalsIgnoreCase(value) || "1".equals(value) || "yes".equalsIgnoreCase(value) || "on".equalsIgnoreCase(value)) {
            return true;
        }
        if ("false".equalsIgnoreCase(value) || "0".equals(value) || "no".equalsIgnoreCase(value) || "off".equalsIgnoreCase(value)) {
            return false;
        }
        return Boolean.parseBoolean(value);
    }

    public static Duration parseDuration(String value) {
        if (value.startsWith("P") || value.startsWith("p") || value.startsWith("-P")) {
            return Duration.parse(value);
        }
        var matcher = DURATION_PATTERN.matcher(value.trim());
        if (!matcher.matches()) {
            throw new IllegalArgumentException("Invalid duration string: " + value);
        }

        long amount = Long.parseLong(matcher.group(1));
        String unit = matcher.group(2);
        if (unit == null) {
            unit = "ms";
        }

        return switch (unit.toLowerCase()) {
            case "ms" -> Duration.ofMillis(amount);
            case "s" -> Duration.ofSeconds(amount);
            case "m" -> Duration.ofMinutes(amount);
            case "h" -> Duration.ofHours(amount);
            case "d" -> Duration.ofDays(amount);
            default -> throw new IllegalArgumentException("Unsupported duration unit: " + unit);
        };
    }

    @SuppressWarnings("unchecked")
    private static <E extends Enum<E>> E parseEnum(String value, Class<E> enumClass) {
        for (E constant : enumClass.getEnumConstants()) {
            if (constant.name().equalsIgnoreCase(value)) {
                return constant;
            }
        }
        throw new IllegalArgumentException("No enum constant " + enumClass.getName() + "." + value);
    }
}
