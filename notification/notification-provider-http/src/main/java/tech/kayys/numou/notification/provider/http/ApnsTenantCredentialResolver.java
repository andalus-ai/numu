package tech.kayys.numou.notification.provider.http;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Host-owned APNs credential boundary. The host resolves credentials from a
 * managed secret/token service and MUST enforce tenant-to-topic authorization.
 * This adapter receives only a short-lived APNs provider JWT, never the signing key.
 */
@FunctionalInterface
public interface ApnsTenantCredentialResolver {
    ApnsTenantCredentials resolve(UUID tenantId);

    record ApnsTenantCredentials(String topic, String bearerToken, Instant expiresAt) {
        public ApnsTenantCredentials {
            topic = required(topic, "topic");
            bearerToken = required(bearerToken, "bearerToken");
            Objects.requireNonNull(expiresAt, "expiresAt is required");
            if (topic.length() > 255 || topic.contains("/") || topic.contains("..")) {
                throw new IllegalArgumentException("APNs topic is invalid");
            }
        }

        @Override public String toString() {
            return "ApnsTenantCredentials[topic=" + topic + ", bearerToken=[REDACTED], expiresAt=" + expiresAt + "]";
        }

        private static String required(String value, String field) {
            Objects.requireNonNull(value, field + " is required");
            String normalized = value.trim();
            if (normalized.isEmpty()) throw new IllegalArgumentException(field + " cannot be blank");
            return normalized;
        }
    }
}
