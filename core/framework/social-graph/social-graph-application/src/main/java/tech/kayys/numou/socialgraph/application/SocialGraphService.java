package tech.kayys.numou.socialgraph.application;

import io.smallrye.mutiny.Uni;
import tech.kayys.numou.ecosystem.domain.party.PartyId;
import tech.kayys.numou.foundation.domain.tenant.TenantId;
import tech.kayys.numou.socialgraph.domain.SocialConnection;
import tech.kayys.numou.socialgraph.domain.SocialConnectionId;
import tech.kayys.numou.socialgraph.domain.SocialConnectionStatus;
import tech.kayys.numou.socialgraph.domain.SocialRelationship;
import tech.kayys.numou.socialgraph.domain.SocialRelationshipId;
import tech.kayys.numou.socialgraph.domain.SocialRelationshipType;
import tech.kayys.numou.socialgraph.spi.SocialRelationshipIdempotencyRecord;
import tech.kayys.numou.socialgraph.spi.SocialRelationshipAuditRecord;
import tech.kayys.numou.socialgraph.spi.SocialGraphAuditRecord;
import tech.kayys.numou.socialgraph.spi.SocialGraphIdempotencyRecord;
import tech.kayys.numou.socialgraph.spi.SocialGraphOutboxEvent;
import tech.kayys.numou.socialgraph.spi.SocialGraphTransaction;
import tech.kayys.numou.socialgraph.spi.SocialGraphUnitOfWork;

import java.time.Clock;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Function;

/**
 * M1 reactive command application service. Actor and tenant context must be
 * resolved by the authenticated host boundary; request payloads cannot choose them.
 */
final class SocialGraphService {
    private final SocialGraphUnitOfWork unitOfWork;
    private final PartyDirectoryPort parties;
    private final SocialGraphAuthorizationPort authorization;
    private final Clock clock;

    SocialGraphService(SocialGraphUnitOfWork unitOfWork, PartyDirectoryPort parties,
            SocialGraphAuthorizationPort authorization, Clock clock) {
        this.unitOfWork = Objects.requireNonNull(unitOfWork, "unitOfWork cannot be null");
        this.parties = Objects.requireNonNull(parties, "parties cannot be null");
        this.authorization = Objects.requireNonNull(authorization, "authorization cannot be null");
        this.clock = Objects.requireNonNull(clock, "clock cannot be null");
    }

    Uni<SocialConnection> requestConnection(SocialGraphActor actor, PartyId recipient, String idempotencyKey) {
        Objects.requireNonNull(actor, "actor cannot be null");
        Objects.requireNonNull(recipient, "recipient cannot be null");
        validateKey(idempotencyKey);
        String fingerprint = "request:" + actor.partyId() + ":" + recipient;
        return authorize(actor, SocialGraphAuthorizationAction.REQUEST_CONNECTION)
                .flatMap(ignored -> requireActive(actor.partyId())).flatMap(actorActive -> {
            if (!actorActive) return failure("Authenticated Party does not exist or is not active");
            return requireActive(recipient).flatMap(recipientActive -> {
                if (!recipientActive) return failure("Recipient Party does not exist or is not active");
                return idempotent(actor.tenantId(), idempotencyKey, fingerprint, tx -> {
                    if (tx.findOpenConnection(actor.tenantId(), actor.partyId(), recipient).isPresent()) {
                        throw new IllegalStateException("A pending or active connection already exists between these Parties");
                    }
                    Instant now = clock.instant();
                    SocialConnection connection = SocialConnection.request(
                            SocialConnectionId.generate(), actor.tenantId(), actor.partyId(), recipient, now);
                    tx.insertConnection(connection);
                    appendEvent(tx, connection, actor.partyId(), "social-graph.connection.requested.v1", now);
                    appendAudit(tx, connection, actor.partyId(), null, connection.status(), "REQUEST", idempotencyKey, now);
                    return connection;
                });
            });
        });
    }

    Uni<SocialConnection> acceptConnection(SocialGraphActor actor, SocialConnectionId id, String key) {
        return transition(actor, id, key, "accept",
                (connection, now) -> connection.accept(actor.partyId(), now),
                "social-graph.connection.accepted.v1");
    }

    Uni<SocialConnection> rejectConnection(SocialGraphActor actor, SocialConnectionId id, String key) {
        return transition(actor, id, key, "reject",
                (connection, now) -> connection.reject(actor.partyId(), now),
                "social-graph.connection.rejected.v1");
    }

    Uni<SocialConnection> cancelConnection(SocialGraphActor actor, SocialConnectionId id, String key) {
        return transition(actor, id, key, "cancel",
                (connection, now) -> connection.cancel(actor.partyId(), now),
                "social-graph.connection.cancelled.v1");
    }

    Uni<SocialConnection> removeConnection(SocialGraphActor actor, SocialConnectionId id, String key) {
        return transition(actor, id, key, "remove",
                (connection, now) -> connection.remove(actor.partyId(), now),
                "social-graph.connection.removed.v1");
    }

    Uni<SocialRelationship> follow(SocialGraphActor actor, PartyId target, String key) {
        return relationshipCommand(actor, target, SocialRelationshipType.FOLLOW, "FOLLOW", key, true);
    }

    Uni<SocialRelationship> unfollow(SocialGraphActor actor, PartyId target, String key) {
        return relationshipCommand(actor, target, SocialRelationshipType.FOLLOW, "UNFOLLOW", key, false);
    }

    Uni<SocialRelationship> block(SocialGraphActor actor, PartyId target, String key) {
        return relationshipCommand(actor, target, SocialRelationshipType.BLOCK, "BLOCK", key, true);
    }

    Uni<SocialRelationship> unblock(SocialGraphActor actor, PartyId target, String key) {
        return relationshipCommand(actor, target, SocialRelationshipType.BLOCK, "UNBLOCK", key, false);
    }

    Uni<SocialRelationship> mute(SocialGraphActor actor, PartyId target, String key) {
        return relationshipCommand(actor, target, SocialRelationshipType.MUTE, "MUTE", key, true);
    }

    Uni<SocialRelationship> unmute(SocialGraphActor actor, PartyId target, String key) {
        return relationshipCommand(actor, target, SocialRelationshipType.MUTE, "UNMUTE", key, false);
    }

    private Uni<SocialRelationship> relationshipCommand(SocialGraphActor actor, PartyId target,
            SocialRelationshipType type, String action, String key, boolean create) {
        Objects.requireNonNull(actor, "actor cannot be null");
        Objects.requireNonNull(target, "target cannot be null");
        validateKey(key);
        String fingerprint = action + ":" + actor.partyId() + ":" + target;
        return authorize(actor, SocialGraphAuthorizationAction.MANAGE_RELATIONSHIP)
                .flatMap(ignored -> requireActive(actor.partyId())).flatMap(actorActive -> {
            if (!actorActive) return failure("Authenticated Party does not exist or is not active");
            return requireActive(target).flatMap(targetActive -> {
                if (!targetActive) return failure("Target Party does not exist or is not active");
                return unitOfWork.execute(tx -> {
                    var prior = tx.findRelationshipIdempotency(actor.tenantId(), key);
                    if (prior.isPresent()) {
                        SocialRelationshipIdempotencyRecord record = prior.get();
                        if (!record.fingerprint().equals(fingerprint)) {
                            throw new IllegalStateException("Idempotency key was already used for a different command");
                        }
                        return tx.findRelationship(actor.tenantId(), record.relationshipId())
                                .orElseThrow(() -> new IllegalStateException("Idempotent relationship result no longer exists"));
                    }

                    Instant now = clock.instant();
                    SocialRelationship result;
                    if (create) {
                        var existing = tx.findActiveRelationship(actor.tenantId(), type, actor.partyId(), target);
                        if (existing.isPresent()) {
                            result = existing.get();
                        } else {
                            result = SocialRelationship.create(SocialRelationshipId.generate(), actor.tenantId(),
                                    type, actor.partyId(), target, now, now, null, "manual", java.util.Map.of());
                            tx.insertRelationship(result);
                            appendRelationshipEvent(tx, result, actor.partyId(), action, now);
                            tx.appendRelationshipAudit(new SocialRelationshipAuditRecord(
                                    UUID.randomUUID(), actor.tenantId(), result.id(), actor.partyId(),
                                    action, key, now));
                        }
                    } else {
                        SocialRelationship current = tx.findActiveRelationship(
                                actor.tenantId(), type, actor.partyId(), target)
                                .orElseThrow(() -> new IllegalStateException("Active directed relationship was not found"));
                        result = current.remove(actor.partyId(), now);
                        tx.updateRelationship(result, current.version());
                        appendRelationshipEvent(tx, result, actor.partyId(), action, now);
                        tx.appendRelationshipAudit(new SocialRelationshipAuditRecord(
                                UUID.randomUUID(), actor.tenantId(), result.id(), actor.partyId(),
                                action, key, now));
                    }
                    tx.saveRelationshipIdempotency(new SocialRelationshipIdempotencyRecord(
                            actor.tenantId(), key, fingerprint, result.id(), now));
                    return result;
                });
            });
        });
    }

    private static void appendRelationshipEvent(SocialGraphTransaction tx, SocialRelationship relationship,
            PartyId actorPartyId, String action, Instant occurredAt) {
        String eventName = action.toLowerCase(java.util.Locale.ROOT);
        String payload = "{\\"relationshipId\\":\\"" + relationship.id()
                + "\\",\\"relationshipType\\":\\"" + relationship.type().name()
                + "\\",\\"sourcePartyId\\":\\"" + relationship.sourcePartyId()
                + "\\",\\"targetPartyId\\":\\"" + relationship.targetPartyId()
                + "\\",\\"status\\":\\"" + relationship.status().name()
                + "\\",\\"aggregateVersion\\":" + relationship.version() + "}";
        tx.appendOutbox(new SocialGraphOutboxEvent(UUID.randomUUID(), relationship.tenantId(),
                actorPartyId, "RELATIONSHIP", relationship.id().value(), relationship.version(),
                "social-graph.relationship." + eventName + ".v1", 1, occurredAt, payload));
    }

    private Uni<SocialConnection> transition(SocialGraphActor actor, SocialConnectionId id, String key,
            String operation, Transition transition, String eventType) {
        Objects.requireNonNull(actor, "actor cannot be null");
        Objects.requireNonNull(id, "id cannot be null");
        validateKey(key);
        String fingerprint = operation + ":" + actor.partyId() + ":" + id;
        return authorize(actor, SocialGraphAuthorizationAction.MANAGE_CONNECTION)
                .flatMap(ignored -> requireActive(actor.partyId())).flatMap(active -> {
            if (!active) return failure("Authenticated Party does not exist or is not active");
            return idempotent(actor.tenantId(), key, fingerprint, tx -> {
                SocialConnection current = tx.findConnection(actor.tenantId(), id)
                        .orElseThrow(SocialConnectionNotFoundException::new);
                SocialConnection changed = transition.apply(current, clock.instant());
                tx.updateConnection(changed, current.version());
                appendEvent(tx, changed, actor.partyId(), eventType, changed.updatedAt());
                appendAudit(tx, changed, actor.partyId(), current.status(), changed.status(),
                        operation.toUpperCase(java.util.Locale.ROOT), key, changed.updatedAt());
                return changed;
            });
        });
    }

    private Uni<SocialConnection> idempotent(TenantId tenantId, String key, String fingerprint,
            Function<SocialGraphTransaction, SocialConnection> work) {
        return unitOfWork.execute(tx -> {
            var prior = tx.findIdempotency(tenantId, key);
            if (prior.isPresent()) {
                SocialGraphIdempotencyRecord record = prior.get();
                if (!record.fingerprint().equals(fingerprint)) {
                    throw new IllegalStateException("Idempotency key was already used for a different command");
                }
                return tx.findConnection(tenantId, record.connectionId())
                        .orElseThrow(SocialConnectionNotFoundException::new);
            }
            SocialConnection result = work.apply(tx);
            tx.saveIdempotency(new SocialGraphIdempotencyRecord(
                    tenantId, key, fingerprint, result.id(), clock.instant()));
            return result;
        });
    }

    private Uni<Void> authorize(SocialGraphActor actor, SocialGraphAuthorizationAction action) {
        return Uni.createFrom().item(() -> {
            authorization.requireAuthorized(actor, action);
            return Boolean.TRUE;
        }).replaceWithVoid();
    }

    private Uni<Boolean> requireActive(PartyId partyId) {
        return parties.isActive(partyId);
    }

    private static void validateKey(String key) {
        if (key == null || key.isBlank() || key.length() > 128) {
            throw new IllegalArgumentException("A non-blank idempotency key of at most 128 characters is required");
        }
    }

    private static <T> Uni<T> failure(String message) {
        return Uni.createFrom().failure(new IllegalArgumentException(message));
    }

    private static void appendEvent(SocialGraphTransaction tx, SocialConnection connection,
            PartyId actorPartyId, String eventType, Instant occurredAt) {
        String payload = "{\"connectionId\":\"" + connection.id()
                + "\",\"requesterPartyId\":\"" + connection.requesterPartyId()
                + "\",\"recipientPartyId\":\"" + connection.recipientPartyId()
                + "\",\"status\":\"" + connection.status().name()
                + "\",\"aggregateVersion\":" + connection.version() + "}";
        tx.appendOutbox(new SocialGraphOutboxEvent(UUID.randomUUID(), connection.tenantId(),
                actorPartyId, connection.id(), connection.version(), eventType, 1, occurredAt, payload));
    }

    private static void appendAudit(SocialGraphTransaction tx, SocialConnection connection,
            PartyId actorPartyId, SocialConnectionStatus fromStatus, SocialConnectionStatus toStatus,
            String action, String idempotencyKey, Instant occurredAt) {
        tx.appendAudit(new SocialGraphAuditRecord(UUID.randomUUID(), connection.tenantId(),
                connection.id(), actorPartyId, fromStatus, toStatus, action, idempotencyKey, occurredAt));
    }

    @FunctionalInterface
    private interface Transition {
        SocialConnection apply(SocialConnection connection, Instant now);
    }
}
