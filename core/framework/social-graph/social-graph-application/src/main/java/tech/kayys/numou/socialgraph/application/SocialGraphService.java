package tech.kayys.numou.socialgraph.application;

import tech.kayys.numou.ecosystem.domain.party.PartyId;
import tech.kayys.numou.foundation.domain.tenant.TenantId;
import tech.kayys.numou.socialgraph.domain.SocialConnection;
import tech.kayys.numou.socialgraph.domain.SocialConnectionId;
import tech.kayys.numou.socialgraph.spi.SocialGraphIdempotencyRecord;
import tech.kayys.numou.socialgraph.spi.SocialGraphOutboxEvent;
import tech.kayys.numou.socialgraph.spi.SocialGraphTransaction;
import tech.kayys.numou.socialgraph.spi.SocialGraphUnitOfWork;

import java.time.Clock;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * M1 command application service. Actor and tenant context must be resolved
 * by the authenticated host boundary; request payloads cannot choose them.
 */
public final class SocialGraphService {
    private final SocialGraphUnitOfWork unitOfWork;
    private final PartyDirectoryPort parties;
    private final Clock clock;

    public SocialGraphService(SocialGraphUnitOfWork unitOfWork, PartyDirectoryPort parties, Clock clock) {
        this.unitOfWork = Objects.requireNonNull(unitOfWork, "unitOfWork cannot be null");
        this.parties = Objects.requireNonNull(parties, "parties cannot be null");
        this.clock = Objects.requireNonNull(clock, "clock cannot be null");
    }

    public SocialConnection requestConnection(SocialGraphActor actor, PartyId recipient, String idempotencyKey) {
        Objects.requireNonNull(actor, "actor cannot be null");
        Objects.requireNonNull(recipient, "recipient cannot be null");
        requireActive(actor.partyId());
        requireActive(recipient);
        String fingerprint = "request:" + actor.partyId() + ":" + recipient;
        return idempotent(actor.tenantId(), idempotencyKey, fingerprint, tx -> {
            if (tx.findOpenConnection(actor.tenantId(), actor.partyId(), recipient).isPresent()) {
                throw new IllegalStateException("A pending or active connection already exists between these Parties");
            }
            Instant now = clock.instant();
            SocialConnection connection = SocialConnection.request(
                    SocialConnectionId.generate(), actor.tenantId(), actor.partyId(), recipient, now);
            tx.insertConnection(connection);
            appendEvent(tx, connection, "social-graph.connection.requested.v1", now);
            return connection;
        });
    }

    public SocialConnection acceptConnection(SocialGraphActor actor, SocialConnectionId id, String key) {
        return transition(actor, id, key, "accept", (connection, now) -> connection.accept(actor.partyId(), now),
                "social-graph.connection.accepted.v1");
    }

    public SocialConnection rejectConnection(SocialGraphActor actor, SocialConnectionId id, String key) {
        return transition(actor, id, key, "reject", (connection, now) -> connection.reject(actor.partyId(), now),
                "social-graph.connection.rejected.v1");
    }

    public SocialConnection cancelConnection(SocialGraphActor actor, SocialConnectionId id, String key) {
        return transition(actor, id, key, "cancel", (connection, now) -> connection.cancel(actor.partyId(), now),
                "social-graph.connection.cancelled.v1");
    }

    public SocialConnection removeConnection(SocialGraphActor actor, SocialConnectionId id, String key) {
        return transition(actor, id, key, "remove", (connection, now) -> connection.remove(actor.partyId(), now),
                "social-graph.connection.removed.v1");
    }

    private SocialConnection transition(SocialGraphActor actor, SocialConnectionId id, String key,
            String operation, Transition transition, String eventType) {
        Objects.requireNonNull(actor, "actor cannot be null");
        Objects.requireNonNull(id, "id cannot be null");
        requireActive(actor.partyId());
        String fingerprint = operation + ":" + id;
        return idempotent(actor.tenantId(), key, fingerprint, tx -> {
            SocialConnection current = tx.findConnection(actor.tenantId(), id)
                    .orElseThrow(SocialConnectionNotFoundException::new);
            SocialConnection changed = transition.apply(current, clock.instant());
            tx.updateConnection(changed, current.version());
            appendEvent(tx, changed, eventType, changed.updatedAt());
            return changed;
        });
    }

    private SocialConnection idempotent(TenantId tenantId, String key, String fingerprint,
            java.util.function.Function<SocialGraphTransaction, SocialConnection> work) {
        if (key == null || key.isBlank() || key.length() > 128) {
            throw new IllegalArgumentException("A non-blank idempotency key of at most 128 characters is required");
        }
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

    private void requireActive(PartyId partyId) {
        if (!parties.isActive(partyId)) {
            throw new IllegalArgumentException("Party does not exist or is not active");
        }
    }

    private static void appendEvent(SocialGraphTransaction tx, SocialConnection connection,
            String eventType, Instant occurredAt) {
        String payload = "{\"connectionId\":\"" + connection.id()
                + "\",\"requesterPartyId\":\"" + connection.requesterPartyId()
                + "\",\"recipientPartyId\":\"" + connection.recipientPartyId()
                + "\",\"status\":\"" + connection.status().name()
                + "\",\"aggregateVersion\":" + connection.version() + "}";
        tx.appendOutbox(new SocialGraphOutboxEvent(UUID.randomUUID(), connection.tenantId(),
                connection.id(), connection.version(), eventType, 1, occurredAt, payload));
    }

    @FunctionalInterface
    private interface Transition {
        SocialConnection apply(SocialConnection connection, Instant now);
    }
}
