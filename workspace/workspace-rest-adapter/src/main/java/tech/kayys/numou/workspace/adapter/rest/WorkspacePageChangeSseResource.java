package tech.kayys.numou.workspace.adapter.rest;

import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.HeaderParam;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.sse.OutboundSseEvent;
import jakarta.ws.rs.sse.Sse;
import jakarta.ws.rs.sse.SseEventSink;
import tech.kayys.numou.workspace.application.WorkspacePageChangeSubscriptionApi;
import tech.kayys.numou.workspace.application.port.WorkspacePageChangeSignal;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Optional SSE transport. The host supplies the tenant-aware fanout implementation; this
 * resource owns bounded connection lifecycle, heartbeat and slow-consumer handling.
 */
@Path("/api/v1/pages/{pageId}/events")
public class WorkspacePageChangeSseResource {
    private final WorkspacePageChangeSubscriptionApi subscriptions;
    private final WorkspaceSseConnectionRegistry connections;

    @Inject
    public WorkspacePageChangeSseResource(WorkspacePageChangeSubscriptionApi subscriptions,
            WorkspaceSseConnectionRegistry connections) {
        this.subscriptions = subscriptions;
        this.connections = connections;
    }

    @GET
    @Produces(MediaType.SERVER_SENT_EVENTS)
    public void subscribe(@PathParam("pageId") UUID pageId,
            @HeaderParam("Last-Event-ID") String lastEventId,
            @Context Sse sse, @Context SseEventSink sink) {
        // Last-Event-ID is deliberately not treated as a durable replay cursor. The client
        // must reconcile through GET /changes after every connection (including reconnects).
        if (lastEventId != null && !lastEventId.isBlank()) {
            try {
                if (Long.parseLong(lastEventId) < 0) throw new NumberFormatException("negative revision");
            } catch (NumberFormatException invalid) {
                sink.close();
                throw new IllegalArgumentException("Last-Event-ID must be a non-negative page revision", invalid);
            }
        }

        // Authenticate and authorize before consuming a scarce SSE connection slot.
        AtomicReference<WorkspaceSseConnectionRegistry.Connection> connectionRef = new AtomicReference<>();
        AutoCloseable registration = subscriptions.subscribe(pageId, signal -> {
            WorkspaceSseConnectionRegistry.Connection connection = connectionRef.get();
            if (connection == null) return; // Any setup-gap signal is recovered by the M35 revision feed.
            OutboundSseEvent event = sse.newEventBuilder()
                    .id(Long.toString(signal.revision()))
                    .name("page-change")
                    .mediaType(MediaType.APPLICATION_JSON_TYPE)
                    .data(ChangeEvent.class, ChangeEvent.from(signal))
                    .build();
            connection.send(() -> sink.send(event));
        });

        WorkspaceSseConnectionRegistry.Connection connection;
        try {
            connection = connections.open(
                    () -> sink.send(sse.newEventBuilder().comment("keepalive").build()),
                    sink::close);
        } catch (WorkspaceSseConnectionRegistry.ConnectionLimitExceededException limit) {
            closeQuietly(registration);
            throw new WebApplicationException("Workspace SSE connection capacity reached", 503);
        } catch (RuntimeException setupFailure) {
            closeQuietly(registration);
            throw setupFailure;
        }
        connectionRef.set(connection);
        connection.onClose(() -> closeQuietly(registration));
        try {
            sink.onClose(connection::close);
            if (sink.isClosed()) connection.close();
        } catch (RuntimeException closeRegistrationFailure) {
            connection.close();
            throw closeRegistrationFailure;
        }
    }

    private static void closeQuietly(AutoCloseable handle) {
        if (handle == null) return;
        try { handle.close(); } catch (Exception ignored) { /* connection cleanup is best-effort */ }
    }

    /** Content-free hint; clients fetch authoritative page revisions through the M35 API. */
    public record ChangeEvent(UUID pageId, long revision, String changeType, java.time.Instant occurredAt) {
        static ChangeEvent from(WorkspacePageChangeSignal signal) {
            return new ChangeEvent(signal.pageId(), signal.revision(), signal.changeType().name(), signal.occurredAt());
        }
    }
}
