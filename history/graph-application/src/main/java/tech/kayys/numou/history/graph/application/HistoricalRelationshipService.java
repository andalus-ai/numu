package tech.kayys.numou.history.graph.application;

import tech.kayys.numou.history.chronology.domain.HistoricalDateRange;
import tech.kayys.numou.history.entities.domain.HistoricalEntityReference;
import tech.kayys.numou.history.entities.domain.HistoricalRelationship;
import tech.kayys.numou.history.entities.domain.HistoricalRelationshipId;
import tech.kayys.numou.history.entities.domain.HistoricalRelationshipKindCode;
import tech.kayys.numou.history.entities.domain.HistoricalRelationshipVocabulary;
import tech.kayys.numou.history.graph.spi.HistoricalRelationshipRepository;

import java.time.Clock;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Application use cases for tenant-private historical relationship research.
 * Tenant identity is resolved from trusted context and never accepted as input.
 */
public final class HistoricalRelationshipService {
    private final HistoricalRelationshipRepository repository;
    private final HistoricalRelationshipTenantContext tenantContext;
    private final HistoricalRelationshipAuthorizationPort authorization;
    private final HistoricalRelationshipVocabulary vocabulary;
    private final Clock clock;

    public HistoricalRelationshipService(
            HistoricalRelationshipRepository repository,
            HistoricalRelationshipTenantContext tenantContext,
            HistoricalRelationshipAuthorizationPort authorization,
            HistoricalRelationshipVocabulary vocabulary,
            Clock clock) {
        this.repository = Objects.requireNonNull(repository, "repository");
        this.tenantContext = Objects.requireNonNull(tenantContext, "tenantContext");
        this.authorization = Objects.requireNonNull(authorization, "authorization");
        this.vocabulary = Objects.requireNonNull(vocabulary, "vocabulary");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    public HistoricalRelationship create(
            HistoricalEntityReference subject,
            HistoricalEntityReference object,
            HistoricalRelationshipKindCode kind,
            HistoricalDateRange validDuring,
            String description) {
        var tenantId = requireTenantId();
        Objects.requireNonNull(subject, "subject");
        Objects.requireNonNull(object, "object");
        authorization.requireCanCreateRelationship(tenantId);
        authorization.requireCanReferenceEntity(tenantId, subject);
        authorization.requireCanReferenceEntity(tenantId, object);
        var relationship = HistoricalRelationship.create(
                subject, object, kind, validDuring, description, clock.instant(), vocabulary);
        if (!repository.saveTenantScoped(tenantId, relationship, 0)) {
            throw new HistoricalRelationshipConflictException("Relationship identity already exists or was concurrently created");
        }
        return relationship;
    }

    public Optional<HistoricalRelationship> find(HistoricalRelationshipId id) {
        var tenantId = requireTenantId();
        Objects.requireNonNull(id, "relationship id");
        authorization.requireCanReadRelationship(tenantId, id);
        return repository.findVisibleTo(tenantId, id);
    }

    public List<HistoricalRelationship> findBySubject(HistoricalEntityReference subject, int limit) {
        var tenantId = requireTenantId();
        Objects.requireNonNull(subject, "subject");
        authorization.requireCanReferenceEntity(tenantId, subject);
        return authorizeRelationshipResults(tenantId, repository.findBySubject(tenantId, subject, limit));
    }

    public List<HistoricalRelationship> findByObject(HistoricalEntityReference object, int limit) {
        var tenantId = requireTenantId();
        Objects.requireNonNull(object, "object");
        authorization.requireCanReferenceEntity(tenantId, object);
        return authorizeRelationshipResults(tenantId, repository.findByObject(tenantId, object, limit));
    }

    private java.util.UUID requireTenantId() {
        return Objects.requireNonNull(tenantContext.requireTenantId(),
                "authenticated tenant context did not provide a tenant ID");
    }

    private List<HistoricalRelationship> authorizeRelationshipResults(
            java.util.UUID tenantId, List<HistoricalRelationship> relationships) {
        relationships.forEach(relationship ->
                authorization.requireCanReadRelationship(tenantId, relationship.id()));
        return List.copyOf(relationships);
    }

    public Optional<HistoricalRelationship> revise(
            HistoricalRelationshipId id,
            HistoricalEntityReference newSubject,
            HistoricalEntityReference newObject,
            HistoricalRelationshipKindCode newKind,
            HistoricalDateRange newValidity,
            String newDescription) {
        var tenantId = requireTenantId();
        Objects.requireNonNull(id, "relationship id");
        authorization.requireCanReviseRelationship(tenantId, id);
        authorization.requireCanReferenceEntity(tenantId, Objects.requireNonNull(newSubject, "newSubject"));
        authorization.requireCanReferenceEntity(tenantId, Objects.requireNonNull(newObject, "newObject"));
        Optional<HistoricalRelationship> current = repository.findVisibleTo(tenantId, id);
        if (current.isEmpty()) return Optional.empty();
        HistoricalRelationship revised = current.get().revise(
                newSubject, newObject, newKind, newValidity, newDescription, clock.instant(), vocabulary);
        if (!repository.saveTenantScoped(tenantId, revised, current.get().revision())) {
            throw new HistoricalRelationshipConflictException("Relationship revision changed concurrently; reload before retrying");
        }
        return Optional.of(revised);
    }
}
