package tech.kayys.syirkah.identity.adapter.outbound.postgres;

import io.quarkus.hibernate.reactive.panache.PanacheEntityBase;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.util.UUID;

@Entity
@Table(name = "identity_permissions")
public class IdentityPermissionEntity extends PanacheEntityBase {
    @Id
    public UUID id;

    @Column(nullable = false, unique = true)
    public String code;
}
