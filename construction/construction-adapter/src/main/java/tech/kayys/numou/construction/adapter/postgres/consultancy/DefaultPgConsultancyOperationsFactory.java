package tech.kayys.numou.construction.adapter.postgres.consultancy;

import io.smallrye.mutiny.Uni;
import io.vertx.mutiny.sqlclient.Row;
import io.vertx.mutiny.sqlclient.SqlConnection;
import io.vertx.mutiny.sqlclient.Tuple;
import tech.kayys.numou.construction.adapter.postgres.idempotency.PgConsultancyOperationsFactory;
import tech.kayys.numou.construction.domain.consultancy.ConsultancyEngagement;
import tech.kayys.numou.construction.domain.consultancy.ConsultancyEngagementId;
import tech.kayys.numou.construction.domain.consultancy.ConsultancyEngagementStatus;
import tech.kayys.numou.construction.spi.idempotency.TransactionalCommandContext;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/** PostgreSQL consultancy operations using only the caller-owned transaction connection. */
public final class DefaultPgConsultancyOperationsFactory implements PgConsultancyOperationsFactory {
    @Override
    public TransactionalCommandContext.ConsultancyOperations create(SqlConnection connection) {
        Objects.requireNonNull(connection, "connection");
        return new Operations(connection);
    }

    private static final class Operations implements TransactionalCommandContext.ConsultancyOperations {
        private static final String INSERT = """
                INSERT INTO construction_consultancy_engagement
                    (tenant_id, engagement_id, project_id, client_organization_id,
                     consultant_organization_id, engagement_number, title, scope_statement,
                     scope_revision, status, cancellation_reason, created_at, updated_at)
                VALUES ($1, $2, $3, $4, $5, $6, $7, $8, $9, $10, $11, now(), now())
                RETURNING tenant_id, engagement_id, project_id, client_organization_id,
                    consultant_organization_id, engagement_number, title, scope_statement,
                    scope_revision, status, cancellation_reason
                """;
        private static final String SELECT_BY_ID = """
                SELECT tenant_id, engagement_id, project_id, client_organization_id,
                    consultant_organization_id, engagement_number, title, scope_statement,
                    scope_revision, status, cancellation_reason
                  FROM construction_consultancy_engagement
                 WHERE tenant_id = $1 AND engagement_id = $2
                """;
        private static final String UPDATE_REVISED_PROPOSAL = """
                UPDATE construction_consultancy_engagement
                   SET title = $3, scope_statement = $4, scope_revision = $5,
                       status = $6, cancellation_reason = NULL, updated_at = now()
                 WHERE tenant_id = $1 AND engagement_id = $2
                   AND status IN ('DRAFT', 'PROPOSED')
                   AND scope_revision = $5 - 1
                RETURNING tenant_id, engagement_id, project_id, client_organization_id,
                    consultant_organization_id, engagement_number, title, scope_statement,
                    scope_revision, status, cancellation_reason
                """;
        private static final String UPDATE_PROPOSED = """
                UPDATE construction_consultancy_engagement
                   SET status = 'PROPOSED', updated_at = now()
                 WHERE tenant_id = $1 AND engagement_id = $2 AND status = 'DRAFT'
                   AND scope_revision = $3
                RETURNING tenant_id, engagement_id, project_id, client_organization_id,
                    consultant_organization_id, engagement_number, title, scope_statement,
                    scope_revision, status, cancellation_reason
                """;
        private static final String UPDATE_ACTIVE = """
                UPDATE construction_consultancy_engagement
                   SET status = 'ACTIVE', updated_at = now()
                 WHERE tenant_id = $1 AND engagement_id = $2 AND status = 'PROPOSED'
                   AND scope_revision = $3
                RETURNING tenant_id, engagement_id, project_id, client_organization_id,
                    consultant_organization_id, engagement_number, title, scope_statement,
                    scope_revision, status, cancellation_reason
                """;
        private static final String UPDATE_COMPLETED = """
                UPDATE construction_consultancy_engagement
                   SET status = 'COMPLETED', updated_at = now()
                 WHERE tenant_id = $1 AND engagement_id = $2 AND status = 'ACTIVE'
                   AND scope_revision = $3
                RETURNING tenant_id, engagement_id, project_id, client_organization_id,
                    consultant_organization_id, engagement_number, title, scope_statement,
                    scope_revision, status, cancellation_reason
                """;
        private static final String UPDATE_CANCELLED = """
                UPDATE construction_consultancy_engagement
                   SET status = 'CANCELLED', cancellation_reason = $3, updated_at = now()
                 WHERE tenant_id = $1 AND engagement_id = $2
                   AND status IN ('DRAFT', 'PROPOSED', 'ACTIVE')
                   AND scope_revision = $4
                RETURNING tenant_id, engagement_id, project_id, client_organization_id,
                    consultant_organization_id, engagement_number, title, scope_statement,
                    scope_revision, status, cancellation_reason
                """;
        private final SqlConnection connection;

        private Operations(SqlConnection connection) { this.connection = connection; }

        @Override
        public Uni<ConsultancyEngagement> saveEngagement(String tenantId, ConsultancyEngagement engagement) {
            requireText(tenantId, "tenantId");
            Objects.requireNonNull(engagement, "engagement");
            return connection.preparedQuery(INSERT).execute(Tuple.of(
                    tenantId, engagement.id().value(), engagement.projectId(),
                    engagement.clientOrganizationId(), engagement.consultantOrganizationId(),
                    engagement.engagementNumber(), engagement.title(), engagement.scopeStatement(),
                    engagement.scopeRevision(), engagement.status().name(), engagement.cancellationReason()))
                    .map(rows -> {
                        if (!rows.iterator().hasNext()) throw new IllegalStateException("Engagement insert returned no row");
                        return map(rows.iterator().next());
                    });
        }

        @Override
        public Uni<Optional<ConsultancyEngagement>> findEngagement(String tenantId, ConsultancyEngagementId id) {
            requireText(tenantId, "tenantId");
            Objects.requireNonNull(id, "engagementId");
            return connection.preparedQuery(SELECT_BY_ID).execute(Tuple.of(tenantId, id.value())).map(rows ->
                    rows.iterator().hasNext() ? Optional.of(map(rows.iterator().next())) : Optional.empty());
        }

        /**
         * Conditional updates are the concurrency boundary: only a transition from the
         * expected persisted state can win. A stale aggregate cannot overwrite a newer
         * lifecycle state, and proposal revisions must advance exactly one revision.
         */
        @Override
        public Uni<ConsultancyEngagement> updateEngagement(String tenantId, ConsultancyEngagement engagement) {
            requireText(tenantId, "tenantId");
            Objects.requireNonNull(engagement, "engagement");
            String status = engagement.status().name();
            String sql = switch (engagement.status()) {
                case DRAFT -> UPDATE_REVISED_PROPOSAL;
                case PROPOSED -> UPDATE_PROPOSED;
                case ACTIVE -> UPDATE_ACTIVE;
                case COMPLETED -> UPDATE_COMPLETED;
                case CANCELLED -> UPDATE_CANCELLED;
            };
            Tuple parameters = switch (engagement.status()) {
                case DRAFT -> Tuple.of(tenantId, engagement.id().value(), engagement.title(),
                        engagement.scopeStatement(), engagement.scopeRevision(), status);
                case PROPOSED, ACTIVE, COMPLETED -> Tuple.of(
                        tenantId, engagement.id().value(), engagement.scopeRevision());
                case CANCELLED -> Tuple.of(tenantId, engagement.id().value(),
                        engagement.cancellationReason(), engagement.scopeRevision());
            };
            return connection.preparedQuery(sql).execute(parameters).map(rows -> {
                if (!rows.iterator().hasNext()) {
                    throw new ConcurrentConsultancyEngagementUpdateException(
                            "Engagement was changed by another command or its lifecycle transition is no longer valid");
                }
                return map(rows.iterator().next());
            });
        }

        private static ConsultancyEngagement map(Row row) {
            String tenantId = row.getString("tenant_id");
            if (tenantId == null || tenantId.isBlank()) throw new IllegalStateException("Persisted engagement has no tenant");
            return ConsultancyEngagement.reconstitute(
                    row.getUUID("engagement_id"), row.getUUID("project_id"),
                    row.getUUID("client_organization_id"), row.getUUID("consultant_organization_id"),
                    row.getString("engagement_number"), row.getString("title"),
                    row.getString("scope_statement"), row.getInteger("scope_revision"),
                    ConsultancyEngagementStatus.valueOf(row.getString("status")),
                    row.getString("cancellation_reason"));
        }

        private static void requireText(String value, String name) {
            if (value == null || value.isBlank()) throw new IllegalArgumentException(name + " is required");
        }
    }
}
