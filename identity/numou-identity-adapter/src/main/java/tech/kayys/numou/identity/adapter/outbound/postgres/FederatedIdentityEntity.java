package tech.kayys.numou.identity.adapter.outbound.postgres;

import io.quarkus.hibernate.reactive.panache.PanacheEntityBase;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.time.Instant;
import java.util.UUID;

/**
 * Link between an external identity provider subject and an internal user
 * ({@code federated_identity} table; one row per provider + subject).
 */
@Entity
@Table(name = "federated_identity", uniqueConstraints =
        @UniqueConstraint(name = "uq_federated_identity_subject", columnNames = {"provider_id", "subject"}))
public class FederatedIdentityEntity extends PanacheEntityBase {

    @Id
    public UUID id;

    @Column(name = "provider_id", nullable = false)
    public UUID providerId;

    @Column(nullable = false)
    public String subject;

    @Column(name = "user_id", nullable = false)
    public UUID userId;

    @Column
    public String email;

    @Column(name = "last_login_at")
    public Instant lastLoginAt;
}
