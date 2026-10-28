package tech.kayys.numou.construction.application.consultancy;

import io.smallrye.mutiny.Uni;
import tech.kayys.numou.construction.application.idempotency.IdempotentCommandExecutor;
import tech.kayys.numou.construction.domain.consultancy.ConsultancyEngagement;
import tech.kayys.numou.construction.domain.consultancy.ConsultancyEngagementId;
import tech.kayys.numou.construction.spi.outbox.TransactionalOutboxEvent;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.util.HexFormat;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Executes the consultancy engagement lifecycle using tenant-scoped persistence,
 * authorization, idempotency and a transactional outbox.
 *
 * <p>The persistence adapter must implement updateEngagement on the same
 * transaction-bound connection as idempotency and outbox operations.</p>
 */
public final class ChangeConsultancyEngagementHandler {
    private static final String OPERATION = "construction.consultancy.engagement.lifecycle.v1";
    private final IdempotentCommandExecutor idempotency;
    private final ConsultancyAuthorization authorization;
    private final Clock clock;

    public ChangeConsultancyEngagementHandler(
            IdempotentCommandExecutor idempotency,
            ConsultancyAuthorization authorization,
            Clock clock) {
        this.idempotency = Objects.requireNonNull(idempotency, "idempotency");
        this.authorization = Objects.requireNonNull(authorization, "authorization");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    public Uni<ConsultancyEngagementReceipt> handle(ChangeConsultancyEngagementCommand command) {
        Objects.requireNonNull(command, "command");
        var context = command.context();
        String tenantId = context.tenantId();
        String fingerprint = fingerprint(command);
        return idempotency.execute(tenantId, command.idempotencyKey(), OPERATION, fingerprint,
                clock.instant(), ConsultancyEngagementReceipt.class, tx ->
                        tx.consultancy().findEngagement(tenantId,
                                        ConsultancyEngagementId.of(command.engagementId()))
                                .flatMap(found -> mutateAndPersist(tx, command, found)));
    }

    private Uni<ConsultancyEngagementReceipt> mutateAndPersist(
            tech.kayys.numou.construction.spi.idempotency.TransactionalCommandContext tx,
            ChangeConsultancyEngagementCommand command,
            Optional<ConsultancyEngagement> found) {
        if (found.isEmpty()) {
            return Uni.createFrom().failure(new ConsultancyEngagementNotFoundException());
        }
        ConsultancyEngagement engagement = found.get();
        ConsultancyAuthorization.Action authorizationAction = authorizationAction(command.action());
        UUID organizationId = command.action() == ChangeConsultancyEngagementCommand.Action.PROPOSE
                || command.action() == ChangeConsultancyEngagementCommand.Action.REVISE_PROPOSAL
                ? engagement.consultantOrganizationId() : engagement.clientOrganizationId();

        return authorization.require(new ConsultancyAuthorization.AuthorizationRequest(
                        command.context().tenantId(), command.context().actorId(), organizationId,
                        authorizationAction))
                .flatMap(ignored -> {
                    switch (command.action()) {
                        case PROPOSE -> engagement.submitProposal();
                        case REVISE_PROPOSAL -> engagement.reviseProposal(command.title(), command.scopeStatement());
                        case ACTIVATE -> engagement.activate();
                        case COMPLETE -> engagement.complete();
                        case CANCEL -> engagement.cancel(command.reason());
                    }
                    return tx.consultancy().updateEngagement(command.context().tenantId(), engagement)
                            .flatMap(saved -> tx.appendOutboxEvent(event(command, saved))
                                    .map(nothing -> new ConsultancyEngagementReceipt(
                                            saved.id().value(), saved.engagementNumber(),
                                            saved.status(), saved.scopeRevision())));
                });
    }

    private TransactionalOutboxEvent event(
            ChangeConsultancyEngagementCommand command, ConsultancyEngagement engagement) {
        String payload = "{\"engagementId\":\"" + engagement.id().value()
                + "\",\"action\":\"" + command.action().name()
                + "\",\"status\":\"" + engagement.status().name()
                + "\",\"scopeRevision\":" + engagement.scopeRevision()
                + ",\"actorId\":\"" + command.context().actorId() + "\"}";
        return new TransactionalOutboxEvent(
                UUID.randomUUID(), command.context().tenantId(), "ConsultancyEngagement",
                engagement.id().value(), "ConsultancyEngagement" + command.action().eventSuffix(),
                payload, clock.instant());
    }

    private static ConsultancyAuthorization.Action authorizationAction(
            ChangeConsultancyEngagementCommand.Action action) {
        return switch (action) {
            case PROPOSE -> ConsultancyAuthorization.Action.PROPOSE_ENGAGEMENT;
            case REVISE_PROPOSAL -> ConsultancyAuthorization.Action.PROPOSE_ENGAGEMENT;
            case ACTIVATE -> ConsultancyAuthorization.Action.ACTIVATE_ENGAGEMENT;
            case COMPLETE -> ConsultancyAuthorization.Action.COMPLETE_ENGAGEMENT;
            case CANCEL -> ConsultancyAuthorization.Action.CANCEL_ENGAGEMENT;
        };
    }

    private static String fingerprint(ChangeConsultancyEngagementCommand command) {
        String canonical = String.join("\n", command.engagementId().toString(),
                command.action().name(), nullToEmpty(command.title()),
                nullToEmpty(command.scopeStatement()), nullToEmpty(command.reason()));
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(canonical.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 is required by the Java platform", impossible);
        }
    }

    private static String nullToEmpty(String value) { return value == null ? "" : value; }

    public static final class ConsultancyEngagementNotFoundException extends RuntimeException {
        public ConsultancyEngagementNotFoundException() {
            super("Consultancy engagement was not found in the current tenant");
        }
    }
}
