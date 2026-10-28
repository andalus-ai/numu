package tech.kayys.numou.construction.application.consultancy;

import io.smallrye.mutiny.Uni;
import tech.kayys.numou.construction.application.idempotency.IdempotentCommandExecutor;
import tech.kayys.numou.construction.domain.consultancy.ConsultancyDeliverable;
import tech.kayys.numou.construction.domain.consultancy.ConsultancyDeliverableId;
import tech.kayys.numou.construction.domain.consultancy.ConsultancyDeliverableHistoryEntry;
import tech.kayys.numou.construction.spi.outbox.TransactionalOutboxEvent;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.util.HexFormat;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/** Executes deliverable lifecycle changes with authorization, idempotency and transactional outbox. */
public final class ChangeConsultancyDeliverableHandler {
    private static final String OPERATION = "construction.consultancy.deliverable.lifecycle.v1";
    private final IdempotentCommandExecutor idempotency;
    private final ConsultancyAuthorization authorization;
    private final Clock clock;

    public ChangeConsultancyDeliverableHandler(
            IdempotentCommandExecutor idempotency,
            ConsultancyAuthorization authorization,
            Clock clock) {
        this.idempotency = Objects.requireNonNull(idempotency, "idempotency");
        this.authorization = Objects.requireNonNull(authorization, "authorization");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    public Uni<ConsultancyDeliverableReceipt> handle(ChangeConsultancyDeliverableCommand command) {
        Objects.requireNonNull(command, "command");
        String tenantId = command.context().tenantId();
        String fingerprint = fingerprint(command);
        return idempotency.execute(tenantId, command.idempotencyKey(), OPERATION, fingerprint,
                clock.instant(), ConsultancyDeliverableReceipt.class, tx ->
                        tx.consultancy().findDeliverable(tenantId,
                                        ConsultancyDeliverableId.of(command.deliverableId()))
                                .flatMap(found -> mutateAndPersist(tx, command, found)));
    }

    private Uni<ConsultancyDeliverableReceipt> mutateAndPersist(
            tech.kayys.numou.construction.spi.idempotency.TransactionalCommandContext tx,
            ChangeConsultancyDeliverableCommand command,
            Optional<ConsultancyDeliverable> found) {
        if (found.isEmpty()) return Uni.createFrom().failure(new ConsultancyDeliverableNotFoundException());
        ConsultancyDeliverable deliverable = found.get();
        boolean consultantAction = command.action() == ChangeConsultancyDeliverableCommand.Action.SUBMIT
                || command.action() == ChangeConsultancyDeliverableCommand.Action.REVISE;
        UUID organizationId = consultantAction
                ? deliverable.consultantOrganizationId() : deliverable.clientOrganizationId();
        ConsultancyAuthorization.Action requiredAction = switch (command.action()) {
            case SUBMIT -> ConsultancyAuthorization.Action.SUBMIT_DELIVERABLE;
            case REVISE -> ConsultancyAuthorization.Action.REVISE_DELIVERABLE;
            case ACCEPT, REJECT -> ConsultancyAuthorization.Action.REVIEW_DELIVERABLE;
        };
        return authorization.require(new ConsultancyAuthorization.AuthorizationRequest(
                        command.context().tenantId(), command.context().actorId(), organizationId, requiredAction))
                .flatMap(ignored -> {
                    switch (command.action()) {
                        case SUBMIT -> deliverable.submit();
                        case ACCEPT -> deliverable.accept(organizationId, command.reviewComment());
                        case REJECT -> deliverable.reject(organizationId, command.reviewComment());
                        case REVISE -> deliverable.revise(command.title(), command.description());
                    }
                    return tx.consultancy().updateDeliverable(command.context().tenantId(), deliverable)
                            .flatMap(saved -> tx.consultancy().appendDeliverableHistory(
                                            command.context().tenantId(), historyEntry(command, saved))
                                    .flatMap(nothing -> tx.appendOutboxEvent(event(command, saved)))
                                    .map(nothing -> new ConsultancyDeliverableReceipt(
                                            saved.id().value(), saved.engagementId().value(),
                                            saved.revision(), saved.status())));
                });
    }

    private ConsultancyDeliverableHistoryEntry historyEntry(
            ChangeConsultancyDeliverableCommand command, ConsultancyDeliverable deliverable) {
        ConsultancyDeliverableHistoryEntry.Action action = switch (command.action()) {
            case SUBMIT -> ConsultancyDeliverableHistoryEntry.Action.SUBMITTED;
            case ACCEPT -> ConsultancyDeliverableHistoryEntry.Action.ACCEPTED;
            case REJECT -> ConsultancyDeliverableHistoryEntry.Action.REJECTED;
            case REVISE -> ConsultancyDeliverableHistoryEntry.Action.REVISED;
        };
        return new ConsultancyDeliverableHistoryEntry(
                UUID.randomUUID(), deliverable.id().value(), deliverable.engagementId().value(),
                deliverable.revision(), deliverable.status(), action, deliverable.title(),
                deliverable.description(), deliverable.reviewedByOrganizationId(),
                deliverable.reviewComment(), command.context().actorId(), clock.instant());
    }

    private TransactionalOutboxEvent event(
            ChangeConsultancyDeliverableCommand command, ConsultancyDeliverable deliverable) {
        // Keep payload fields constrained to UUIDs, integers and enums to avoid hand-built JSON escaping bugs.
        String payload = "{\"deliverableId\":\"" + deliverable.id().value()
                + "\",\"engagementId\":\"" + deliverable.engagementId().value()
                + "\",\"action\":\"" + command.action().name()
                + "\",\"status\":\"" + deliverable.status().name()
                + "\",\"revision\":" + deliverable.revision()
                + ",\"actorId\":\"" + command.context().actorId() + "\"}";
        return new TransactionalOutboxEvent(
                UUID.randomUUID(), command.context().tenantId(), "ConsultancyDeliverable",
                deliverable.id().value(), "ConsultancyDeliverable." + command.action().eventSuffix(),
                payload, clock.instant());
    }

    private static String fingerprint(ChangeConsultancyDeliverableCommand command) {
        String canonical = String.join("\n", command.deliverableId().toString(),
                command.action().name(), nullToEmpty(command.title()),
                nullToEmpty(command.description()), nullToEmpty(command.reviewComment()));
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(canonical.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 is required by the Java platform", impossible);
        }
    }

    private static String nullToEmpty(String value) { return value == null ? "" : value; }

    public static final class ConsultancyDeliverableNotFoundException extends RuntimeException {
        public ConsultancyDeliverableNotFoundException() {
            super("Consultancy deliverable was not found in the current tenant");
        }
    }
}
