package tech.kayys.numou.identity.adapter.outbound.postgres;

import io.quarkus.hibernate.reactive.panache.Panache;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import tech.kayys.numou.identity.application.port.FederatedIdentityRepository;
import tech.kayys.numou.identity.domain.idp.FederatedIdentityLink;

import java.util.Optional;
import java.util.UUID;

@ApplicationScoped
public class PostgresFederatedIdentityRepository implements FederatedIdentityRepository {

    @Override
    public Uni<Optional<FederatedIdentityLink>> findByProviderAndSubject(UUID providerId, String subject) {
        return FederatedIdentityEntity.<FederatedIdentityEntity>find(
                        "providerId = ?1 and subject = ?2", providerId, subject)
                .firstResult()
                .map(entity -> Optional.ofNullable(entity).map(this::toDomain));
    }

    @Override
    public Uni<Optional<FederatedIdentityLink>> findByUserId(UUID userId) {
        return FederatedIdentityEntity.<FederatedIdentityEntity>find(
                        "userId = ?1", userId)
                .firstResult()
                .map(entity -> Optional.ofNullable(entity).map(this::toDomain));
    }

    @Override
    public Uni<Void> save(FederatedIdentityLink link) {
        return FederatedIdentityEntity.<FederatedIdentityEntity>findById(link.id())
                .chain(existing -> {
                    FederatedIdentityEntity entity = existing != null ? existing : new FederatedIdentityEntity();
                    entity.id = link.id();
                    entity.providerId = link.providerId();
                    entity.subject = link.subject();
                    entity.userId = link.userId();
                    entity.email = link.email();
                    entity.lastLoginAt = link.lastLoginAt();
                    return Panache.getSession().chain(session -> session.persist(entity));
                });
    }

    private FederatedIdentityLink toDomain(FederatedIdentityEntity entity) {
        return new FederatedIdentityLink(
                entity.id,
                entity.providerId,
                entity.subject,
                entity.userId,
                entity.email,
                entity.lastLoginAt
        );
    }
}
