package tech.kayys.numou.notification.provider.http;

import java.security.PrivateKey;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/** Host-owned VAPID key boundary. The host must authorize the tenant and manage key rotation. */
@FunctionalInterface
public interface WebPushVapidCredentialResolver {
    VapidCredentials resolve(UUID tenantId);

    record VapidCredentials(String subject, PrivateKey privateKey, byte[] publicKey, Instant expiresAt) {
        public VapidCredentials {
            subject = Objects.requireNonNull(subject, "subject is required").trim();
            if (!(subject.startsWith("mailto:") || subject.startsWith("https://")) || subject.length() > 255)
                throw new IllegalArgumentException("VAPID subject must be a mailto: or HTTPS URI");
            Objects.requireNonNull(privateKey, "privateKey is required");
            Objects.requireNonNull(publicKey, "publicKey is required");
            Objects.requireNonNull(expiresAt, "expiresAt is required");
            if (publicKey.length != 65 || publicKey[0] != 0x04)
                throw new IllegalArgumentException("VAPID public key must be an uncompressed P-256 key");
            publicKey = publicKey.clone();
        }
        @Override public byte[] publicKey() { return publicKey.clone(); }
        @Override public String toString() {
            return "VapidCredentials[subject=" + subject + ", privateKey=[REDACTED], publicKey=[REDACTED], expiresAt=" + expiresAt + "]";
        }
    }
}
