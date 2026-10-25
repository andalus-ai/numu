package tech.kayys.numou.omnichannel.core;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/**
 * Validates transport-neutral webhook headers before they reach provider
 * verifiers. HTTP adapters must still enforce limits before buffering/parsing.
 */
final class WebhookHeaders {
    static final int MAX_HEADER_COUNT = 100;
    static final int MAX_NAME_LENGTH = 256;
    static final int MAX_VALUE_LENGTH = 8_192;
    static final int MAX_TOTAL_CHARACTERS = 65_536;

    private WebhookHeaders() {
    }

    static Map<String, String> validatedCopy(Map<String, String> source) {
        Objects.requireNonNull(source, "headers must not be null");
        if (source.size() > MAX_HEADER_COUNT) {
            throw new IllegalArgumentException("headers must not contain more than 100 entries");
        }

        Map<String, String> copy = new LinkedHashMap<>();
        Map<String, String> namesByLowerCase = new LinkedHashMap<>();
        int totalCharacters = 0;
        for (Map.Entry<String, String> entry : source.entrySet()) {
            String name = Objects.requireNonNull(entry.getKey(), "header name must not be null");
            String value = Objects.requireNonNull(entry.getValue(), "header value must not be null");
            if (name.isEmpty() || name.length() > MAX_NAME_LENGTH || !isToken(name)) {
                throw new IllegalArgumentException("header name is invalid or exceeds the allowed length");
            }
            if (value.length() > MAX_VALUE_LENGTH || containsUnsafeControl(value)) {
                throw new IllegalArgumentException("header value is invalid or exceeds the allowed length");
            }

            String normalizedName = name.toLowerCase(Locale.ROOT);
            if (namesByLowerCase.putIfAbsent(normalizedName, name) != null) {
                throw new IllegalArgumentException("headers contain case-insensitive duplicate names");
            }
            totalCharacters += name.length() + value.length();
            if (totalCharacters > MAX_TOTAL_CHARACTERS) {
                throw new IllegalArgumentException("headers exceed the total allowed size");
            }
            copy.put(name, value);
        }
        return Map.copyOf(copy);
    }

    private static boolean isToken(String value) {
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            boolean alphaNumeric = c >= 'a' && c <= 'z'
                    || c >= 'A' && c <= 'Z'
                    || c >= '0' && c <= '9';
            if (!alphaNumeric && "!#$%&'*+-.^_`|~".indexOf(c) < 0) {
                return false;
            }
        }
        return true;
    }

    private static boolean containsUnsafeControl(String value) {
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if ((c < 0x20 && c != '\t') || c == 0x7f) {
                return true;
            }
        }
        return false;
    }
}
