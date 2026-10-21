package tech.kayys.numou.identity.adapter.outbound.postgres;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.quarkus.hibernate.reactive.panache.Panache;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import tech.kayys.numou.identity.application.port.FederatedIdentityRepository;
import tech.kayys.numou.identity.domain.idp.FederatedIdentityLink;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@ApplicationScoped
public class PostgresFederatedIdentityRepository implements FederatedIdentityRepository {

    private final ObjectMapper objectMapper;

    @Inject
    public PostgresFederatedIdentityRepository(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

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
        return persist(link, null);
    }

    @Override
    public Uni<Void> save(FederatedIdentityLink link, java.util.Map<String, String> claims) {
        return persist(link, claims);
    }

    /**
     * Upserts one link row. {@code claims} are written as JSON when present; a {@code null}
     * claims map leaves the stored custom attributes untouched, so a plain save never wipes
     * attributes a federated login captured earlier.
     */
    private Uni<Void> persist(FederatedIdentityLink link, java.util.Map<String, String> claims) {
        return FederatedIdentityEntity.<FederatedIdentityEntity>findById(link.id())
                .chain(existing -> {
                    FederatedIdentityEntity entity = existing != null ? existing : new FederatedIdentityEntity();
                    entity.id = link.id();
                    entity.providerId = link.providerId();
                    entity.subject = link.subject();
                    entity.userId = link.userId();
                    entity.email = link.email();
                    entity.lastLoginAt = link.lastLoginAt();
                    if (claims != null) {
                        try {
                            entity.attributes = objectMapper.writeValueAsString(claims);
                        } catch (JsonProcessingException exception) {
                            throw new IllegalStateException(
                                    "Could not serialize custom attributes for federated link", exception);
                        }
                    }
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
