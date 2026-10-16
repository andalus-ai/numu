package tech.kayys.syirkah.document.adapter.outbound.postgres;

import io.quarkus.hibernate.reactive.panache.Panache;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.persistence.LockModeType;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@ApplicationScoped
public class PostgresDocumentOutboxRepository implements DocumentOutboxRepository {
    @Override
    public Uni<List<DocumentOutboxMessage>> claimPending(Instant now, Instant leaseUntil, int limit) {
        if (limit < 1 || limit > 500) {
            return Uni.createFrom().failure(new IllegalArgumentException("Outbox batch size must be 1..500"));
        }
        return Panache.withTransaction(() -> Panache.getSession()
                .chain(session -> session.createNativeQuery("""
                                SELECT id
                                FROM document_outbox
                                WHERE next_attempt_at <= :now
                                  AND (status = 'PENDING'
                                       OR (status = 'PROCESSING' AND lease_until <= :now))
                                ORDER BY created_at, id
                                LIMIT :limit
                                FOR UPDATE SKIP LOCKED
                                """, UUID.class)
                        .setParameter("now", now)
                        .setParameter("limit", limit)
                        .getResultList())
                .chain(ids -> {
                    Uni<List<DocumentOutboxMessage>> messages = Uni.createFrom().item(new java.util.ArrayList<>());
                    for (var id : ids) {
                        messages = messages.chain(claimed -> DocumentOutboxEntity.<DocumentOutboxEntity>findById(id)
                                .onItem().ifNull().failWith(() -> new IllegalStateException(
                                        "Claimed outbox message disappeared: " + id
                                ))
                                .invoke(entity -> {
                                    entity.status = "PROCESSING";
                                    entity.leaseUntil = leaseUntil;
                                })
                                .map(entity -> {
                                    claimed.add(toMessage(entity));
                                    return claimed;
                                }));
                    }
                    return messages.map(List::copyOf);
                }));
    }

    @Override
    public Uni<Void> markPublished(UUID id, Instant publishedAt) {
        return Panache.withTransaction(() -> DocumentOutboxEntity.<DocumentOutboxEntity>findById(id)
                        .onItem().ifNull().failWith(() -> new IllegalArgumentException("Outbox message not found"))
                        .invoke(entity -> {
                            requireProcessing(entity);
                            entity.status = "PUBLISHED";
                            entity.publishedAt = publishedAt;
                            entity.leaseUntil = null;
                            entity.lastError = null;
                        })
                        .replaceWithVoid());
    }

    @Override
    public Uni<Void> recordFailure(UUID id, String error, Instant nextAttemptAt, int maximumAttempts) {
        if (maximumAttempts < 1) {
            return Uni.createFrom().failure(new IllegalArgumentException("maximumAttempts must be positive"));
        }
        return Panache.withTransaction(() -> DocumentOutboxEntity.<DocumentOutboxEntity>findById(id)
                        .onItem().ifNull().failWith(() -> new IllegalArgumentException("Outbox message not found"))
                        .invoke(entity -> {
                            requireProcessing(entity);
                            entity.attemptCount++;
                            entity.lastError = truncate(error);
                            entity.leaseUntil = null;
                            if (entity.attemptCount >= maximumAttempts) {
                                entity.status = "DEAD";
                            } else {
                                entity.status = "PENDING";
                                entity.nextAttemptAt = nextAttemptAt;
                            }
                        })
                        .replaceWithVoid());
    }

    private static DocumentOutboxMessage toMessage(DocumentOutboxEntity entity) {
        return new DocumentOutboxMessage(
                entity.id,
                entity.eventId,
                entity.tenantId,
                entity.aggregateType,
                entity.aggregateId,
                entity.eventType,
                entity.payload,
                entity.createdAt,
                entity.attemptCount
        );
    }

    private static void requireProcessing(DocumentOutboxEntity entity) {
        if (!"PROCESSING".equals(entity.status)) {
            throw new IllegalStateException("Outbox message is not claimed");
        }
    }

    private static String truncate(String error) {
        if (error == null || error.isBlank()) {
            return "Unspecified event delivery failure";
        }
        var sanitized = error.strip();
        return sanitized.length() <= 2000 ? sanitized : sanitized.substring(0, 2000);
    }
}
