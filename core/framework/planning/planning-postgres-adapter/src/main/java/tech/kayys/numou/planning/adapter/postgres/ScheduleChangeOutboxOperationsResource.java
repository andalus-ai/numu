package tech.kayys.numou.planning.adapter.postgres;

import jakarta.annotation.security.RolesAllowed;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

/**
 * Restricted operational view of the schedule-change outbox.
 *
 * <p>The host application must configure an authentication mechanism and map trusted
 * operators to the {@code platform-operations} role. This resource intentionally omits
 * event payloads, tenant identifiers, and last-error text.
 */
@Path("/internal/operations/planning/outbox")
@ApplicationScoped
@RolesAllowed("platform-operations")
@Produces(MediaType.APPLICATION_JSON)
public class ScheduleChangeOutboxOperationsResource {
    private final JdbcScheduleChangeOutboxRelayStore store;

    @Inject
    public ScheduleChangeOutboxOperationsResource(JdbcScheduleChangeOutboxRelayStore store) {
        this.store = store;
    }

    @GET
    public OutboxStatus getStatus() {
        var snapshot = store.healthSnapshot();
        return new OutboxStatus(
                snapshot.pendingCount(),
                snapshot.leasedCount(),
                snapshot.retryingCount(),
                snapshot.deadLetterCount(),
                snapshot.oldestPendingAgeSeconds());
    }

    public record OutboxStatus(
            long pendingCount,
            long leasedCount,
            long retryingCount,
            long deadLetterCount,
            long oldestPendingAgeSeconds) {
    }
}
