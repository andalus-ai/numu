package tech.kayys.numou.identity.adapter.outbound.postgres;

import io.quarkus.hibernate.reactive.panache.Panache;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import tech.kayys.numou.identity.application.port.IdentityProviderConfigRepository;
import tech.kayys.numou.identity.domain.idp.IdentityProviderConfig;
import tech.kayys.numou.identity.domain.idp.IdentityProviderConfigId;
import tech.kayys.numou.identity.domain.idp.IdentityProviderType;

import java.util.List;
import java.util.Optional;

@ApplicationScoped
public class PostgresIdentityProviderConfigRepository implements IdentityProviderConfigRepository {

    @Override
    public Uni<Optional<IdentityProviderConfig>> findById(IdentityProviderConfigId id) {
        return IdentityProviderConfigEntity.<IdentityProviderConfigEntity>findById(id.value())
                .map(entity -> Optional.ofNullable(entity).map(this::toDomain));
    }

    @Override
    public Uni<Optional<IdentityProviderConfig>> findByName(String name) {
        return IdentityProviderConfigEntity.<IdentityProviderConfigEntity>find("name", name)
                .firstResult()
                .map(entity -> Optional.ofNullable(entity).map(this::toDomain));
    }

    @Override
    public Uni<List<IdentityProviderConfig>> findEnabled() {
        return IdentityProviderConfigEntity.<IdentityProviderConfigEntity>find("enabled", true)
                .list()
                .map(list -> list.stream().map(this::toDomain).toList());
    }

    @Override
    public Uni<Void> save(IdentityProviderConfig config) {
        return IdentityProviderConfigEntity.<IdentityProviderConfigEntity>findById(config.id().value())
                .chain(existing -> {
                    IdentityProviderConfigEntity entity = existing != null ? existing : new IdentityProviderConfigEntity();
                    entity.id = config.id().value();
                    entity.name = config.name();
                    entity.type = config.type().name();
                    entity.connectionConfigJson = config.connectionConfigJson();
                    entity.enabled = config.isEnabled();
                    entity.scimEndpoint = config.scimEndpoint();
                    entity.createdAt = config.createdAt();

                    return Panache.getSession().chain(session -> session.persist(entity));
                });
    }

    private IdentityProviderConfig toDomain(IdentityProviderConfigEntity entity) {
        return IdentityProviderConfig.reconstitute(
                IdentityProviderConfigId.of(entity.id),
                entity.name,
                IdentityProviderType.valueOf(entity.type.toUpperCase(java.util.Locale.ROOT)),
                entity.connectionConfigJson,
                entity.enabled,
                entity.scimEndpoint,
                entity.createdAt
        );
    }
}
