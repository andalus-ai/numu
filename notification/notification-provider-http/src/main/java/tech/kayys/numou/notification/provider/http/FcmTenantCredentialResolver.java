package tech.kayys.numou.notification.provider.http;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Host-owned credential boundary. Implementations resolve tenant-scoped FCM project
 * credentials from a secret manager or workload identity; credentials are never persisted
 * by the notification capability.
 */
@FunctionalInterface
public interface FcmTenantCredentialResolver {
    FcmTenantCredentials resolve(UUID tenantId);

    record FcmTenantCredentials(String projectId, String accessToken, Instant expiresAt) {
        public FcmTenantCredentials {
            projectId = required(projectId, "projectId");
            accessToken = required(accessToken, "accessToken");
            Objects.requireNonNull(expiresAt, "expiresAt is required");
        }

        @Override public String toString() {
            return "FcmTenantCredentials[projectId=" + projectId + ", accessToken=[REDACTED], expiresAt=" + expiresAt + "]";
        }

        private static String required(String value, String field) {
            Objects.requireNonNull(value, field + " is required");
            String normalized = value.trim();
            if (normalized.isEmpty()) throw new IllegalArgumentException(field + " cannot be blank");
            return normalized;
        }
    }
}
