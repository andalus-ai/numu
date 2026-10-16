package tech.kayys.syirkah.commerce.promotion.domain.compiler;

import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;

public final class ParameterReader {

    private ParameterReader() {}

    public static Object require(Map<String, Object> parameters, String name) {
        if (!parameters.containsKey(name)) {
            throw new PromotionCompilationException(
                    "Missing required parameter: " + name);
        }
        return parameters.get(name);
    }

    public static String string(Map<String, Object> parameters, String name) {
        Object value = require(parameters, name);
        if (!(value instanceof String s) || s.isBlank()) {
            throw new PromotionCompilationException(
                    "Parameter '" + name + "' must be a non-blank string");
        }
        return s;
    }

    public static UUID uuid(Map<String, Object> parameters, String name) {
        Object value = require(parameters, name);
        try {
            if (value instanceof UUID id) {
                return id;
            }
            return UUID.fromString(value.toString());
        } catch (IllegalArgumentException ex) {
            throw new PromotionCompilationException(
                    "Parameter '" + name + "' must be a UUID");
        }
    }

    public static BigDecimal decimal(Map<String, Object> parameters, String name) {
        Object value = require(parameters, name);
        return switch (value) {
            case BigDecimal bd -> bd;
            case Number n -> BigDecimal.valueOf(n.doubleValue());
            case String s -> new BigDecimal(s);
            default -> throw new PromotionCompilationException(
                    "Parameter '" + name + "' must be a number");
        };
    }
}
