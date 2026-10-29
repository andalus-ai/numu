package tech.kayys.numou.ecosystem.domain.organization;

import java.io.Serializable;
import java.util.Objects;

/**
 * Extensible organization classification expressed as a namespaced code.
 *
 * <p>This is intentionally not an enum: jurisdictions and industries can
 * introduce classifications without requiring a core release. Examples
 * include {@code numou:company}, {@code numou:cooperative},
 * {@code indonesia:foundation}, and {@code industry:educational-institution}.
 */
public record OrganizationType(String scheme, String code) implements Serializable {

    public OrganizationType {
        scheme = requireText(scheme, "scheme");
        code = requireText(code, "code");
    }

    public static OrganizationType of(String scheme, String code) {
        return new OrganizationType(scheme, code);
    }

    private static String requireText(String value, String field) {
        Objects.requireNonNull(value, field + " cannot be null");
        String normalized = value.trim();
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException(field + " cannot be blank");
        }
        return normalized;
    }

    public String qualifiedCode() {
        return scheme + ":" + code;
    }
}
