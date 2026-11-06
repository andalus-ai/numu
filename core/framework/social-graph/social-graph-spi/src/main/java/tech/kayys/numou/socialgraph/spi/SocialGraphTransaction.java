package tech.kayys.numou.socialgraph.spi;

import tech.kayys.numou.foundation.domain.tenant.TenantId;
import tech.kayys.numou.ecosystem.domain.party.PartyId;
import tech.kayys.numou.socialgraph.domain.SocialConnection;
import tech.kayys.numou.socialgraph.domain.SocialConnectionId;
import tech.kayys.numou.socialgraph.domain.SocialRelationship;
import tech.kayys.numou.socialgraph.domain.SocialRelationshipId;
import tech.kayys.numou.socialgraph.domain.SocialRelationshipType;

import java.util.Optional;

/** Transaction-bound operations; implementations must share one physical transaction. */
public interface SocialGraphTransaction {
    Optional<SocialRelationship> findRelationship(TenantId tenantId, SocialRelationshipId id);
    Optional<SocialRelationship> findActiveRelationship(
            TenantId tenantId, SocialRelationshipType type, PartyId source, PartyId target);
    void insertRelationship(SocialRelationship relationship);
    void updateRelationship(SocialRelationship relationship, int expectedVersion);

    Optional<SocialConnection> findConnection(TenantId tenantId, SocialConnectionId id);
    Optional<SocialConnection> findOpenConnection(TenantId tenantId, PartyId first, PartyId second);
    void insertConnection(SocialConnection connection);
    void updateConnection(SocialConnection connection, int expectedVersion);
    Optional<SocialGraphIdempotencyRecord> findIdempotency(TenantId tenantId, String key);
    void saveIdempotency(SocialGraphIdempotencyRecord record);
    void appendOutbox(SocialGraphOutboxEvent event);
    void appendAudit(SocialGraphAuditRecord record);
}
