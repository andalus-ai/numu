package tech.kayys.numou.identity.adapter.outbound.postgres;

import io.quarkus.hibernate.reactive.panache.PanacheEntityBase;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "oauth_client_credential")
public class OAuthClientCredentialEntity extends PanacheEntityBase {
    @Id public UUID id;
    @Column(name = "tenant_id", nullable = false) public UUID tenantId;
    @Column(name = "application_id", nullable = false) public UUID applicationId;
    @Column(name = "client_id", nullable = false, length = 96) public String clientId;
    @Column(name = "secret_hash", nullable = false, length = 64) public String secretHash;
    @Column(name = "secret_prefix", nullable = false, length = 32) public String secretPrefix;
    @Column(name = "scopes", nullable = false, columnDefinition = "text") public String scopes;
    @Column(nullable = false, length = 16) public String status;
    @Column(name = "credential_version", nullable = false) public long credentialVersion;
    @Column(name = "created_at", nullable = false) public Instant createdAt;
    @Column(name = "expires_at") public Instant expiresAt;
    @Column(name = "rotated_at") public Instant rotatedAt;
    @Column(name = "revoked_at") public Instant revokedAt;
    @Version @Column(name = "row_version", nullable = false) public long rowVersion;
}
