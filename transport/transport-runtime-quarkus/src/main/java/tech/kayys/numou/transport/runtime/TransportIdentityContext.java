package tech.kayys.numou.transport.runtime;

import java.util.Objects;

/**
 * Verified caller context supplied by the host's authentication integration.
 *
 * <p>Values must be populated from validated identity claims or an equivalent trusted
 * authentication mechanism. They are never read from request bodies, query parameters,
 * or caller-controlled tenant headers.</p>
 */
public record TransportIdentityContext(String tenantId, String actorId) {
    public TransportIdentityContext {
        tenantId = required(tenantId, "tenantId", 128);
        actorId = required(actorId, "actorId", 128);
    }

    public static TransportIdentityContext from(String principalName, Object tenantClaim, Object actorClaim) {
        required(principalName, "authenticated principal", 256);
        return new TransportIdentityContext(claim(tenantClaim, "tenant claim"),
                claim(actorClaim, "actor claim"));
    }

    private static String claim(Object value, String label) {
        if (!(value instanceof String text)) {
            throw new SecurityException("authenticated identity is missing a trusted " + label);
        }
        return required(text, label, 128);
    }

    private static String required(String value, String label, int maxLength) {
        Objects.requireNonNull(value, label);
        String normalized = value.trim();
        if (normalized.isEmpty() || normalized.length() > maxLength) {
            throw new SecurityException(label + " must contain 1-" + maxLength + " characters");
        }
        return normalized;
    }
}
