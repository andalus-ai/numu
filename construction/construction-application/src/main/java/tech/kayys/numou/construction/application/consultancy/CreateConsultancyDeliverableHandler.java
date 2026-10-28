package tech.kayys.numou.construction.application.consultancy;

import io.smallrye.mutiny.Uni;
import tech.kayys.numou.construction.application.idempotency.IdempotentCommandExecutor;
import tech.kayys.numou.construction.domain.consultancy.ConsultancyDeliverable;
import tech.kayys.numou.construction.domain.consultancy.ConsultancyDeliverableHistoryEntry;
import tech.kayys.numou.construction.domain.consultancy.ConsultancyEngagement;
import tech.kayys.numou.construction.domain.consultancy.ConsultancyEngagementId;
import tech.kayys.numou.construction.domain.consultancy.ConsultancyEngagementStatus;
import tech.kayys.numou.construction.spi.idempotency.TransactionalCommandContext;
import tech.kayys.numou.construction.spi.outbox.TransactionalOutboxEvent;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.util.HexFormat;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/** Creates deliverables only for active engagements, with authorization, idempotency, history and outbox. */
public final class CreateConsultancyDeliverableHandler {
    private static final String OPERATION = "construction.consultancy.deliverable.create.v1";
    private final IdempotentCommandExecutor idempotency;
    private final ConsultancyAuthorization authorization;
    private final Clock clock;

    public CreateConsultancyDeliverableHandler(IdempotentCommandExecutor idempotency,
            ConsultancyAuthorization authorization, Clock clock) {
        this.idempotency = Objects.requireNonNull(idempotency, "idempotency");
        this.authorization = Objects.requireNonNull(authorization, "authorization");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    public Uni<ConsultancyDeliverableReceipt> handle(CreateConsultancyDeliverableCommand command) {
        Objects.requireNonNull(command, "command");
        String tenantId = command.context().tenantId();
        return idempotency.execute(tenantId, command.idempotencyKey(), OPERATION,
                fingerprint(command), clock.instant(), ConsultancyDeliverableReceipt.class,
                tx -> tx.consultancy().findEngagement(tenantId, ConsultancyEngagementId.of(command.engagementId()))
                        .flatMap(found -> create(tx, command, found)));
    }

    private Uni<ConsultancyDeliverableReceipt> create(TransactionalCommandContext tx,
            CreateConsultancyDeliverableCommand command, Optional<ConsultancyEngagement> found) {
        if (found.isEmpty()) return Uni.createFrom().failure(new ConsultancyEngagementNotFoundException());
        ConsultancyEngagement engagement = found.get();
        if (engagement.status() != ConsultancyEngagementStatus.ACTIVE) {
            return Uni.createFrom().failure(new IllegalStateException(
                    "Deliverables can only be created for an active consultancy engagement"));
        }
        return authorization.require(new ConsultancyAuthorization.AuthorizationRequest(
                        command.context().tenantId(), command.context().actorId(),
                        engagement.consultantOrganizationId(), ConsultancyAuthorization.Action.CREATE_DELIVERABLE))
                .flatMap(ignored -> {
                    ConsultancyDeliverable deliverable = ConsultancyDeliverable.create(
                            ConsultancyEngagementId.of(engagement.id().value()),
                            engagement.clientOrganizationId(), engagement.consultantOrganizationId(),
                            command.code(), command.title(), command.description());
                    return tx.consultancy().saveDeliverable(command.context().tenantId(), deliverable)
                            .flatMap(saved -> tx.consultancy().appendDeliverableHistory(
                                            command.context().tenantId(), snapshot(saved,
                                                    ConsultancyDeliverableHistoryEntry.Action.CREATED,
                                                    command.context().actorId()))
                                    .flatMap(nothing -> tx.appendOutboxEvent(event(command, saved)))
                                    .map(nothing -> new ConsultancyDeliverableReceipt(
                                            saved.id().value(), saved.engagementId().value(),
                                            saved.revision(), saved.status())));
                });
    }

    private ConsultancyDeliverableHistoryEntry snapshot(ConsultancyDeliverable d,
            ConsultancyDeliverableHistoryEntry.Action action, UUID actorId) {
        return new ConsultancyDeliverableHistoryEntry(UUID.randomUUID(), d.id().value(),
                d.engagementId().value(), d.revision(), d.status(), action, d.title(), d.description(),
                d.reviewedByOrganizationId(), d.reviewComment(), actorId, clock.instant());
    }

    private TransactionalOutboxEvent event(CreateConsultancyDeliverableCommand command, ConsultancyDeliverable d) {
        String payload = "{\"deliverableId\":\"" + d.id().value()
                + "\",\"engagementId\":\"" + d.engagementId().value()
                + "\",\"status\":\"" + d.status().name()
                + "\",\"revision\":" + d.revision()
                + ",\"actorId\":\"" + command.context().actorId() + "\"}";
        return new TransactionalOutboxEvent(UUID.randomUUID(), command.context().tenantId(),
                "ConsultancyDeliverable", d.id().value(), "ConsultancyDeliverable.Created", payload, clock.instant());
    }

    private static String fingerprint(CreateConsultancyDeliverableCommand c) {
        String canonical = String.join("\n", c.engagementId().toString(), c.code(), c.title(), c.description());
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(canonical.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 is required by the Java platform", impossible);
        }
    }

    public static final class ConsultancyEngagementNotFoundException extends RuntimeException {
        public ConsultancyEngagementNotFoundException() {
            super("Consultancy engagement was not found in the current tenant");
        }
    }
}
