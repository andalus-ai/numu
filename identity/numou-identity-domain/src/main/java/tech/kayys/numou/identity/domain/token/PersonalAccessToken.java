package tech.kayys.numou.identity.domain.token;

import tech.kayys.numou.foundation.domain.audit.AuditMeta;
import tech.kayys.numou.foundation.domain.entity.AbstractAggregateRoot;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * A long-lived, revocable credential owned by a User or ServiceAccount.
 * Only the SHA-256 hash of the token is stored — the raw token is returned
 * exactly once at creation time.
 */
public final class PersonalAccessToken extends AbstractAggregateRoot<PersonalAccessTokenId> {

    private final PersonalAccessTokenId id;
    private final String principalType;
    private final UUID principalId;
    private final String label;
    private final String tokenHash;
    private final String displayPrefix;
    private TokenStatus status;
    private final Instant createdAt;
    private final Instant expiresAt;
    private Instant lastUsedAt;
    private Instant revokedAt;
    private AuditMeta auditMeta;

    private PersonalAccessToken(
            PersonalAccessTokenId id,
            String principalType,
            UUID principalId,
            String label,
            String tokenHash,
            String displayPrefix,
            TokenStatus status,
            Instant createdAt,
            Instant expiresAt,
            Instant lastUsedAt,
            Instant revokedAt,
            AuditMeta auditMeta
    ) {
        super(Objects.requireNonNull(id, "id cannot be null"));
        this.id = id;
        this.principalType = Objects.requireNonNull(principalType, "principalType cannot be null");
        this.principalId = Objects.requireNonNull(principalId, "principalId cannot be null");
        this.label = Objects.requireNonNull(label, "label cannot be null");
        this.tokenHash = Objects.requireNonNull(tokenHash, "tokenHash cannot be null");
        this.displayPrefix = Objects.requireNonNull(displayPrefix, "displayPrefix cannot be null");
        this.status = Objects.requireNonNull(status, "status cannot be null");
        this.createdAt = Objects.requireNonNull(createdAt, "createdAt cannot be null");
        this.expiresAt = expiresAt;
        this.lastUsedAt = lastUsedAt;
        this.revokedAt = revokedAt;
        this.auditMeta = auditMeta != null ? auditMeta : deriveFallback(principalId, createdAt, revokedAt);
    }

    /**
     * Fallback for rows persisted before audit columns existed: the owning principal
     * created the token, and the last known state change is the revocation (if any).
     */
    private static AuditMeta deriveFallback(UUID principalId, Instant createdAt, Instant revokedAt) {
        var initial = AuditMeta.initial(principalId.toString(), createdAt);
        return revokedAt != null
                ? new AuditMeta(initial.createdBy(), createdAt, initial.createdBy(), revokedAt)
                : initial;
    }

    public static PersonalAccessToken issue(
            PersonalAccessTokenId id,
            String principalType,
            UUID principalId,
            String label,
            String tokenHash,
            String displayPrefix,
            Instant expiresAt,
            Instant occurredAt
    ) {
        var token = new PersonalAccessToken(
                id,
                principalType,
                principalId,
                label,
                tokenHash,
                displayPrefix,
                TokenStatus.ACTIVE,
                occurredAt,
                expiresAt,
                null,
                null,
                null
        );

        token.raise(new PersonalAccessTokenIssued(
                UUID.randomUUID(),
                occurredAt,
                id,
                principalType,
                principalId,
                label
        ));

        return token;
    }

    public static PersonalAccessToken reconstitute(
            PersonalAccessTokenId id,
            String principalType,
            UUID principalId,
            String label,
            String tokenHash,
            String displayPrefix,
            TokenStatus status,
            Instant createdAt,
            Instant expiresAt,
            Instant lastUsedAt,
            Instant revokedAt
    ) {
        return reconstitute(
                id, principalType, principalId, label, tokenHash, displayPrefix,
                status, createdAt, expiresAt, lastUsedAt, revokedAt, null
        );
    }

    /**
     * @param auditMeta persisted audit metadata; {@code null} falls back to values derived
     *                   from the owning principal and creation/revocation timestamps.
     */
    public static PersonalAccessToken reconstitute(
            PersonalAccessTokenId id,
            String principalType,
            UUID principalId,
            String label,
            String tokenHash,
            String displayPrefix,
            TokenStatus status,
            Instant createdAt,
            Instant expiresAt,
            Instant lastUsedAt,
            Instant revokedAt,
            AuditMeta auditMeta
    ) {
        return new PersonalAccessToken(
                id,
                principalType,
                principalId,
                label,
                tokenHash,
                displayPrefix,
                status,
                createdAt,
                expiresAt,
                lastUsedAt,
                revokedAt,
                auditMeta
        );
    }

    public void revoke(Instant occurredAt) {
        revoke(principalId.toString(), occurredAt);
    }

    /**
     * Revokes the token and records who revoked it in the audit metadata.
     *
     * @param actor     actor identifier (user id or system name); {@code null} keeps the previous actor
     * @param occurredAt transition timestamp
     */
    public void revoke(String actor, Instant occurredAt) {
        if (status == TokenStatus.REVOKED) {
            throw new IllegalStateException("Token is already revoked");
        }
        this.status = TokenStatus.REVOKED;
        this.revokedAt = Objects.requireNonNull(occurredAt);
        this.auditMeta = new AuditMeta(
                auditMeta.createdBy(),
                auditMeta.createdAt(),
                actor != null && !actor.isBlank() ? actor : auditMeta.updatedBy(),
                occurredAt
        );

        raise(new PersonalAccessTokenRevoked(
                UUID.randomUUID(),
                occurredAt,
                id,
                principalType,
                principalId
        ));
    }

    public void touch(Instant occurredAt) {
        this.lastUsedAt = Objects.requireNonNull(occurredAt);
    }

    public boolean isActive(Instant now) {
        if (status != TokenStatus.ACTIVE) {
            return false;
        }
        return expiresAt == null || expiresAt.isAfter(now);
    }

    @Override
    public PersonalAccessTokenId id() {
        return id;
    }

    public String principalType() {
        return principalType;
    }

    public UUID principalId() {
        return principalId;
    }

    public String label() {
        return label;
    }

    public String tokenHash() {
        return tokenHash;
    }

    public String displayPrefix() {
        return displayPrefix;
    }

    public TokenStatus status() {
        return status;
    }

    public Instant createdAt() {
        return createdAt;
    }

    public Instant expiresAt() {
        return expiresAt;
    }

    public Instant lastUsedAt() {
        return lastUsedAt;
    }

    public Instant revokedAt() {
        return revokedAt;
    }

    /**
     * Standard audit metadata ({@code createdBy/createdAt/updatedBy/updatedAt}) for this token.
     */
    public AuditMeta auditMeta() {
        return auditMeta;
    }
}
