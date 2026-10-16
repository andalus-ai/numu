package tech.kayys.syirkah.identity.adapter.outbound.postgres;

import io.quarkus.hibernate.reactive.panache.PanacheEntityBase;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.util.UUID;

/**
 * Persistence model - deliberately separate from the User aggregate.
 * The domain layer never sees @Entity/@Column; mapping between the
 * two lives entirely in PostgresUserRepository.
 */
@Entity
@Table(name = "identity_users")
public class UserEntity extends PanacheEntityBase {

    @Id
    public UUID id;

    @Column(nullable = false, unique = true)
    public String email;

    @Column(name = "password_hash", nullable = false)
    public String passwordHash;

    @Column(name = "display_name", nullable = false)
    public String displayName;

    @Column(nullable = false)
    public String status;

    @Version
    @Column(nullable = false)
    public long version;

}
