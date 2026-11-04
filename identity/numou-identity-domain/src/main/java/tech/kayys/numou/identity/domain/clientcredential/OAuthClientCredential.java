package tech.kayys.numou.identity.domain.clientcredential;

import tech.kayys.numou.foundation.domain.entity.AbstractAggregateRoot;

import java.time.Instant;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;

/**
 * Tenant- and application-bound OAuth2 client credential metadata.
 *
 * <p>Only a one-way hash of the high-entropy client secret is stored here.
 * The raw secret is generated and returned once by the application layer and
 * must never be added to this aggregate, an event, or persistence metadata.</p>
 */
public final class OAuthClientCredential extends AbstractAggregateRoot<OAuthClientCredentialId> {
    private final OAuthClientCredentialId id;
    private final UUID tenantId;
    private final UUID applicationId;
    private final String clientId;
    private String secretHash;
    private String secretPrefix;
    private Set<String> scopes;
    private OAuthClientCredentialStatus status;
    private long credentialVersion;
    private final Instant createdAt;
    private final Instant expiresAt;
    private Instant rotatedAt;
    private Instant revokedAt;

    private OAuthClientCredential(
            OAuthClientCredentialId id,
            UUID tenantId,
            UUID applicationId,
            String clientId,
            String secretHash,
            String secretPrefix,
            Set<String> scopes,
            OAuthClientCredentialStatus status,
            long credentialVersion,
            Instant createdAt,
            Instant expiresAt,
            Instant rotatedAt,
            Instant revokedAt) {
        super(Objects.requireNonNull(id, "id cannot be null"));
        this.id = id;
        this.tenantId = Objects.requireNonNull(tenantId, "tenantId cannot be null");
        this.applicationId = Objects.requireNonNull(applicationId, "applicationId cannot be null");
        this.clientId = requireText(clientId, "clientId");
        this.secretHash = requireHash(secretHash);
        this.secretPrefix = requireText(secretPrefix, "secretPrefix");
        this.scopes = normalizeScopes(scopes);
        this.status = Objects.requireNonNull(status, "status cannot be null");
        if (credentialVersion < 1) throw new IllegalArgumentException("credentialVersion must be positive");
        this.credentialVersion = credentialVersion;
        this.createdAt = Objects.requireNonNull(createdAt, "createdAt cannot be null");
        if (expiresAt != null && !expiresAt.isAfter(createdAt)) {
            throw new IllegalArgumentException("expiresAt must be after createdAt");
        }
        this.expiresAt = expiresAt;
        this.rotatedAt = rotatedAt;
        this.revokedAt = revokedAt;
        if (status == OAuthClientCredentialStatus.REVOKED && revokedAt == null) {
            throw new IllegalArgumentException("revokedAt is required for revoked credentials");
        }
    }

    public static OAuthClientCredential issue(
            OAuthClientCredentialId id,
            UUID tenantId,
            UUID applicationId,
            String clientId,
            String secretHash,
            String secretPrefix,
            Set<String> scopes,
            Instant expiresAt,
            Instant occurredAt) {
        var credential = new OAuthClientCredential(id, tenantId, applicationId, clientId,
                secretHash, secretPrefix, scopes, OAuthClientCredentialStatus.ACTIVE,
                1, occurredAt, expiresAt, null, null);
        credential.raise(new OAuthClientCredentialIssued(UUID.randomUUID(), occurredAt,
                id, tenantId, applicationId, clientId, 1));
        return credential;
    }

    public void rotate(String nextSecretHash, String nextSecretPrefix,
                       Instant nextExpiresAt, Instant occurredAt) {
        requireActive(occurredAt);
        String verifiedHash = requireHash(nextSecretHash);
        String verifiedPrefix = requireText(nextSecretPrefix, "secretPrefix");
        if (nextExpiresAt != null && !nextExpiresAt.isAfter(occurredAt)) {
            throw new IllegalArgumentException("expiresAt must be after rotation time");
        }
        this.secretHash = verifiedHash;
        this.secretPrefix = verifiedPrefix;
        this.expiresAtCheck(nextExpiresAt, occurredAt);
        this.rotatedAt = Objects.requireNonNull(occurredAt, "occurredAt cannot be null");
        this.credentialVersion = Math.addExact(credentialVersion, 1);
        raise(new OAuthClientCredentialRotated(UUID.randomUUID(), occurredAt,
                id, tenantId, applicationId, clientId, credentialVersion));
    }

    private void expiresAtCheck(Instant nextExpiresAt, Instant occurredAt) {
        // Expiry is immutable for a credential generation; a rotation creates a new generation.
        if (nextExpiresAt != null && !nextExpiresAt.isAfter(occurredAt)) {
            throw new IllegalArgumentException("expiresAt must be after rotation time");
        }
    }

    public void revoke(Instant occurredAt) {
        Objects.requireNonNull(occurredAt, "occurredAt cannot be null");
        if (status == OAuthClientCredentialStatus.REVOKED) return;
        status = OAuthClientCredentialStatus.REVOKED;
        revokedAt = occurredAt;
        raise(new OAuthClientCredentialRevoked(UUID.randomUUID(), occurredAt,
                id, tenantId, applicationId, clientId, credentialVersion));
    }

    public boolean isUsableAt(Instant instant) {
        Objects.requireNonNull(instant, "instant cannot be null");
        return status == OAuthClientCredentialStatus.ACTIVE
                && (expiresAt == null || expiresAt.isAfter(instant));
    }

    private void requireActive(Instant instant) {
        Objects.requireNonNull(instant, "occurredAt cannot be null");
        if (!isUsableAt(instant)) {
            throw new IllegalStateException("Only an active, unexpired credential can be rotated");
        }
    }

    private static Set<String> normalizeScopes(Set<String> values) {
        Objects.requireNonNull(values, "scopes cannot be null");
        if (values.isEmpty()) throw new IllegalArgumentException("at least one scope is required");
        TreeSet<String> normalized = new TreeSet<>();
        for (String value : values) {
            String scope = requireText(value, "scope");
            if (!scope.matches("[a-z][a-z0-9]*(?:[.:][a-z0-9][a-z0-9-]*)*")) {
                throw new IllegalArgumentException("invalid scope: " + scope);
            }
            normalized.add(scope);
        }
        return Set.copyOf(normalized);
    }

    private static String requireHash(String value) {
        Objects.requireNonNull(value, "secretHash cannot be null");
        if (!value.matches("[a-fA-F0-9]{64}")) {
            throw new IllegalArgumentException("secretHash must be a SHA-256 hex digest");
        }
        return value.toLowerCase(java.util.Locale.ROOT);
    }

    private static String requireText(String value, String field) {
        Objects.requireNonNull(value, field + " cannot be null");
        String normalized = value.trim();
        if (normalized.isEmpty()) throw new IllegalArgumentException(field + " cannot be blank");
        return normalized;
    }

    public OAuthClientCredentialId id() { return id; }
    public UUID tenantId() { return tenantId; }
    public UUID applicationId() { return applicationId; }
    public String clientId() { return clientId; }
    public String secretHash() { return secretHash; }
    public String secretPrefix() { return secretPrefix; }
    public Set<String> scopes() { return scopes; }
    public OAuthClientCredentialStatus status() { return status; }
    public long credentialVersion() { return credentialVersion; }
    public Instant createdAt() { return createdAt; }
    public Instant expiresAt() { return expiresAt; }
    public Instant rotatedAt() { return rotatedAt; }
    public Instant revokedAt() { return revokedAt; }
}
