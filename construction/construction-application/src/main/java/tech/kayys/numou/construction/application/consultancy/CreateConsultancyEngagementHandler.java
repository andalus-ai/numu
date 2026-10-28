package tech.kayys.numou.construction.application.consultancy;

import io.smallrye.mutiny.Uni;
import tech.kayys.numou.construction.application.idempotency.IdempotentCommandExecutor;
import tech.kayys.numou.construction.domain.consultancy.ConsultancyEngagement;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.util.HexFormat;
import java.util.Objects;

/**
 * Creates a consultancy engagement with tenant/actor authorization and a
 * transaction-bound idempotency outcome. Persistence adapters must implement
 * TransactionalCommandContext.consultancy() on the same transaction connection.
 */
public final class CreateConsultancyEngagementHandler {
    private static final String OPERATION = "construction.consultancy.engagement.create.v1";
    private final IdempotentCommandExecutor idempotency;
    private final ConsultancyAuthorization authorization;
    private final Clock clock;

    public CreateConsultancyEngagementHandler(
            IdempotentCommandExecutor idempotency,
            ConsultancyAuthorization authorization,
            Clock clock) {
        this.idempotency = Objects.requireNonNull(idempotency, "idempotency");
        this.authorization = Objects.requireNonNull(authorization, "authorization");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    public Uni<ConsultancyEngagementReceipt> handle(CreateConsultancyEngagementCommand command) {
        Objects.requireNonNull(command, "command");
        var context = command.context();
        String tenantId = context.tenantId();
        String fingerprint = fingerprint(command);
        return idempotency.execute(
                tenantId, command.idempotencyKey(), OPERATION, fingerprint,
                clock.instant(), ConsultancyEngagementReceipt.class,
                tx -> authorization.require(new ConsultancyAuthorization.AuthorizationRequest(
                                tenantId, context.actorId(), command.clientOrganizationId(),
                                ConsultancyAuthorization.Action.CREATE_ENGAGEMENT))
                        .flatMap(ignored -> {
                            ConsultancyEngagement engagement = ConsultancyEngagement.create(
                                    command.projectId(), command.clientOrganizationId(),
                                    command.consultantOrganizationId(), command.engagementNumber(),
                                    command.title(), command.scopeStatement());
                            return tx.consultancy().saveEngagement(tenantId, engagement)
                                    .map(saved -> new ConsultancyEngagementReceipt(
                                            saved.id().value(), saved.engagementNumber(),
                                            saved.status(), saved.scopeRevision()));
                        }));
    }

    private static String fingerprint(CreateConsultancyEngagementCommand command) {
        String canonical = String.join("\n",
                command.projectId().toString(),
                command.clientOrganizationId().toString(),
                command.consultantOrganizationId().toString(),
                command.engagementNumber(),
                command.title(),
                command.scopeStatement());
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(canonical.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 is required by the Java platform", impossible);
        }
    }
}
