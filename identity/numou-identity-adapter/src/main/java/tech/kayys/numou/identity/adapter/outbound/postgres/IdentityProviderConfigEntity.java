package tech.kayys.numou.identity.adapter.outbound.postgres;

import io.quarkus.hibernate.reactive.panache.PanacheEntityBase;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "identity_provider_config")
public class IdentityProviderConfigEntity extends PanacheEntityBase {

    @Id
    public UUID id;

    @Column(nullable = false, unique = true)
    public String name;

    @Column(nullable = false)
    public String type;

    @Column(name = "connection_config_json", nullable = false)
    public String connectionConfigJson;

    @Column(nullable = false)
    public boolean enabled;

    @Column(name = "scim_endpoint")
    public String scimEndpoint;

    @Column(name = "created_at", nullable = false)
    public Instant createdAt;
}
