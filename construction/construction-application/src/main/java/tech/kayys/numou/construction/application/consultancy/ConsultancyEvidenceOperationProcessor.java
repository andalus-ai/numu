package tech.kayys.numou.construction.application.consultancy;

import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import tech.kayys.numou.construction.application.consultancy.ConsultancyEvidenceOperationStore.ClaimedConsultancyEvidenceOperation;
import tech.kayys.numou.construction.spi.consultancy.ConsultancyEvidenceLinkStore;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;

/**
 * Executes one claimed evidence saga operation.
 *
 * <p>The operation is marked LINKED only after Document's idempotent link
 * operation returns a persisted version-pinned relationship. Authorization is
 * re-evaluated immediately before the cross-capability write.</p>
 */
@ApplicationScoped
public class ConsultancyEvidenceOperationProcessor {
    private static final Duration BASE_RETRY_DELAY = Duration.ofSeconds(15);
    private static final Duration MAX_RETRY_DELAY = Duration.ofMinutes(15);

    private final ConsultancyEvidenceOperationStore operations;
    private final ConsultancyEvidenceAuthorization authorization;
    private final ConsultancyEvidenceLinkStore evidenceLinks;

    @Inject
    public ConsultancyEvidenceOperationProcessor(ConsultancyEvidenceOperationStore operations,
            ConsultancyEvidenceAuthorization authorization, ConsultancyEvidenceLinkStore evidenceLinks) {
        this.operations = Objects.requireNonNull(operations);
        this.authorization = Objects.requireNonNull(authorization);
        this.evidenceLinks = Objects.requireNonNull(evidenceLinks);
    }

    /**
     * Processes a single current claim. A false result means the lease was lost
     * before a state transition could be committed; the caller must not treat it
     * as a successful completion.
     */
    public Uni<Boolean> process(ClaimedConsultancyEvidenceOperation claim, int maxAttempts) {
        Objects.requireNonNull(claim, "claim");
        if (maxAttempts < 1) throw new IllegalArgumentException("maxAttempts must be positive");

        ConsultancyEvidenceOperation operation = claim.operation();
        var deliverableAccess = new ConsultancyEvidenceAuthorization.DeliverableAccess(
                operation.tenantId(), operation.requestedBy(), operation.requestingOrganizationId(),
                operation.deliverableId(), ConsultancyEvidenceAuthorization.Action.ATTACH_EVIDENCE);
        var documentAccess = new ConsultancyEvidenceAuthorization.DocumentAccess(
                operation.tenantId(), operation.requestedBy(), operation.requestingOrganizationId(),
                operation.documentId(), operation.documentVersionId(),
                ConsultancyEvidenceAuthorization.Action.ATTACH_EVIDENCE);

        return authorization.requireDeliverableAccess(deliverableAccess)
                .chain(() -> authorization.requireDocumentAccess(documentAccess))
                .chain(() -> evidenceLinks.attach(
                        operation.tenantId(), operation.deliverableId(), operation.documentId(),
                        operation.documentVersionId(), operation.relationship(), operation.requestedBy()))
                .chain(link -> {
                    if (link == null
                            || !operation.deliverableId().equals(link.deliverableId())
                            || !operation.documentId().equals(link.documentId())
                            || !operation.documentVersionId().equals(link.documentVersionId())
                            || !operation.relationship().equals(link.relationship())
                            || !operation.requestedBy().equals(link.linkedBy())) {
                        return Uni.createFrom().failure(new IllegalStateException(
                                "Document returned an evidence link that does not match the requested operation"));
                    }
                    return operations.markLinked(claim, Instant.now());
                })
                .onFailure(ConsultancyEvidenceAccessDeniedException.class)
                .recoverWithUni(failure -> operations.markTerminalFailure(
                        claim, safeMessage(failure), Instant.now()))
                .onFailure()
                .recoverWithUni(failure -> {
                    Instant failedAt = Instant.now();
                    String reason = safeMessage(failure);
                    if (operation.attempts() >= maxAttempts) {
                        return operations.markTerminalFailure(claim, reason, failedAt);
                    }
                    return operations.markRetryableFailure(
                            claim, reason, failedAt.plus(retryDelay(operation.attempts())), failedAt);
                });
    }

    private static Duration retryDelay(int attempt) {
        int exponent = Math.max(0, Math.min(attempt - 1, 10));
        Duration delay = BASE_RETRY_DELAY.multipliedBy(1L << exponent);
        return delay.compareTo(MAX_RETRY_DELAY) > 0 ? MAX_RETRY_DELAY : delay;
    }

    private static String safeMessage(Throwable failure) {
        String message = failure.getMessage();
        if (message == null || message.isBlank()) message = failure.getClass().getSimpleName();
        return message.length() > 2000 ? message.substring(0, 2000) : message;
    }
}
