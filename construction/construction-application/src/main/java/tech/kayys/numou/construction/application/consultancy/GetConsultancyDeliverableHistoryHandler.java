package tech.kayys.numou.construction.application.consultancy;

import io.smallrye.mutiny.Uni;
import tech.kayys.numou.construction.domain.consultancy.ConsultancyDeliverable;
import tech.kayys.numou.construction.domain.consultancy.ConsultancyDeliverableHistoryEntry;
import tech.kayys.numou.construction.domain.consultancy.ConsultancyDeliverableId;
import tech.kayys.numou.construction.spi.idempotency.TransactionalCommandContext;
import tech.kayys.numou.construction.spi.idempotency.TransactionalCommandRunner;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Reads an append-only deliverable timeline only after tenant-scoped resource lookup
 * and organization-level authorization. Runtime adapters remain responsible for
 * authentication and supplying a trusted tenant/actor context.
 */
public final class GetConsultancyDeliverableHistoryHandler {
    private final TransactionalCommandRunner transactions;
    private final ConsultancyAuthorization authorization;

    public GetConsultancyDeliverableHistoryHandler(
            TransactionalCommandRunner transactions,
            ConsultancyAuthorization authorization) {
        this.transactions = Objects.requireNonNull(transactions, "transactions");
        this.authorization = Objects.requireNonNull(authorization, "authorization");
    }

    public Uni<List<ConsultancyDeliverableHistoryEntry>> handle(GetConsultancyDeliverableHistoryQuery query) {
        Objects.requireNonNull(query, "query");
        String tenantId = query.context().tenantId();
        return transactions.inTransaction(tx -> tx.consultancy()
                .findDeliverable(tenantId, ConsultancyDeliverableId.of(query.deliverableId()))
                .flatMap(found -> authorizeAndRead(tx, query, found)));
    }

    private Uni<List<ConsultancyDeliverableHistoryEntry>> authorizeAndRead(
            TransactionalCommandContext tx,
            GetConsultancyDeliverableHistoryQuery query,
            Optional<ConsultancyDeliverable> found) {
        if (found.isEmpty()) return Uni.createFrom().failure(
                new ConsultancyDeliverableNotFoundException());
        ConsultancyDeliverable deliverable = found.get();
        boolean party = query.organizationId().equals(deliverable.clientOrganizationId())
                || query.organizationId().equals(deliverable.consultantOrganizationId());
        if (!party) {
            // Use the same result as a missing resource to avoid confirming cross-tenant/third-party IDs.
            return Uni.createFrom().failure(new ConsultancyDeliverableNotFoundException());
        }
        return authorization.require(new ConsultancyAuthorization.AuthorizationRequest(
                        query.context().tenantId(), query.context().actorId(), query.organizationId(),
                        ConsultancyAuthorization.Action.VIEW_DELIVERABLE_HISTORY))
                .flatMap(ignored -> tx.consultancy().listDeliverableHistory(
                        query.context().tenantId(), ConsultancyDeliverableId.of(query.deliverableId())));
    }

    public static final class ConsultancyDeliverableNotFoundException extends RuntimeException {
        public ConsultancyDeliverableNotFoundException() {
            super("Consultancy deliverable was not found in the current tenant");
        }
    }
}
