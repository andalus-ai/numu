package tech.kayys.numou.construction.application.consultancy;

import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import tech.kayys.numou.construction.application.consultancy.ConsultancyEvidenceOperationStore.ClaimedConsultancyEvidenceOperation;
import tech.kayys.numou.construction.spi.consultancy.ConsultancyEvidenceLink;
import tech.kayys.numou.construction.spi.consultancy.ConsultancyEvidenceLinkStore;

import java.time.Clock;
import java.time.Duration;
import java.util.Objects;

/**
 * Executes one already-claimed consultancy evidence operation.
 *
 * <p>The scheduler/claim loop is deliberately separate: this processor never
 * claims work itself. Document attachment is idempotent, so a successful
 * Document write followed by lease loss is safe to retry. Only a confirmed
 * persisted link followed by a successful fenced transition is reported as
 * linked.</p>
 */
@ApplicationScoped
public class ConsultancyEvidenceOperationWorker {
    private final ConsultancyEvidenceOperationStore operations;
    private final ConsultancyEvidenceAuthorization authorization;
    private final ConsultancyEvidenceLinkStore evidenceLinks;

    @Inject
    public ConsultancyEvidenceOperationWorker(
            ConsultancyEvidenceOperationStore operations,
            ConsultancyEvidenceAuthorization authorization,
            ConsultancyEvidenceLinkStore evidenceLinks) {
        this.operations = Objects.requireNonNull(operations);
        this.authorization = Objects.requireNonNull(authorization);
        this.evidenceLinks = Objects.requireNonNull(evidenceLinks);
    }

    public Uni<ConsultancyEvidenceLink> process(
            ClaimedConsultancyEvidenceOperation claim, Clock clock, Duration retryDelay) {
        Objects.requireNonNull(claim, "claim");
        Objects.requireNonNull(clock, "clock");
        Objects.requireNonNull(retryDelay, "retryDelay");
        if (retryDelay.isNegative()) {
            return Uni.createFrom().failure(new IllegalArgumentException("retryDelay cannot be negative"));
        }

        var operation = claim.operation();
        var actor = operation.requestedBy();
        var organization = operation.requestingOrganizationId();

        return authorization.requireDeliverableAccess(
                        new ConsultancyEvidenceAuthorization.DeliverableAccess(
                                operation.tenantId(), actor, organization, operation.deliverableId(),
                                ConsultancyEvidenceAuthorization.Action.ATTACH_EVIDENCE))
                .chain(() -> authorization.requireDocumentAccess(
                        new ConsultancyEvidenceAuthorization.DocumentAccess(
                                operation.tenantId(), actor, organization, operation.documentId(),
                                operation.documentVersionId(),
                                ConsultancyEvidenceAuthorization.Action.ATTACH_EVIDENCE)))
                .chain(() -> evidenceLinks.attach(
                        operation.tenantId(), operation.deliverableId(), operation.documentId(),
                        operation.documentVersionId(), operation.relationship(), actor))
                .chain(link -> operations.markLinked(claim, clock.instant())
                        .chain(updated -> updated
                                ? Uni.createFrom().item(link)
                                : Uni.createFrom().failure(new StaleConsultancyEvidenceLeaseException(
                                        "Evidence link exists but this worker no longer owns the operation lease"))))
                .onFailure().recoverWithUni(failure -> recordFailure(
                        claim, failure, clock.instant(), retryDelay));
    }

    private Uni<ConsultancyEvidenceLink> recordFailure(
            ClaimedConsultancyEvidenceOperation claim, Throwable failure,
            java.time.Instant now, Duration retryDelay) {
        if (failure instanceof StaleConsultancyEvidenceLeaseException) {
            return Uni.createFrom().failure(failure);
        }
        if (failure instanceof ConsultancyEvidenceAccessDeniedException
                || failure instanceof ConsultancyEvidencePermanentFailureException) {
            return operations.markTerminalFailure(claim, safeMessage(failure), now)
                    .chain(updated -> Uni.createFrom().failure(updated ? failure
                            : new StaleConsultancyEvidenceLeaseException(
                                    "Authorization failed after this worker lost its lease")));
        }
        return operations.markRetryableFailure(
                        claim, safeMessage(failure), now.plus(retryDelay), now)
                .chain(updated -> Uni.createFrom().failure(updated ? failure
                        : new StaleConsultancyEvidenceLeaseException(
                                "Operation failed after this worker lost its lease")));
    }

    private static String safeMessage(Throwable failure) {
        String message = failure.getMessage();
        if (message == null || message.isBlank()) {
            message = failure.getClass().getSimpleName();
        }
        return message.length() > 2000 ? message.substring(0, 2000) : message;
    }

    public static final class StaleConsultancyEvidenceLeaseException extends RuntimeException {
        public StaleConsultancyEvidenceLeaseException(String message) {
            super(message);
        }
    }
}
