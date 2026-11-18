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
import tech.kayys.numou.workspace.application.port.WorkspacePageChangeSubscription;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Optional SSE transport. The host must compose a tenant-aware subscription port and a shared
 * heartbeat scheduler; this resource does not create an in-memory broker or per-connection thread.
 */
@Path("/api/v1/pages/{pageId}/events")
public class WorkspacePageChangeSseResource {
    private final WorkspacePageChangeSubscriptionApi subscriptions;
    private final WorkspaceSseHeartbeatScheduler heartbeats;

    @Inject
    public WorkspacePageChangeSseResource(WorkspacePageChangeSubscriptionApi subscriptions,
            WorkspaceSseHeartbeatScheduler heartbeats) {
        this.subscriptions = subscriptions;
        this.heartbeats = heartbeats;
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

        AtomicBoolean sending = new AtomicBoolean();
        AtomicReference<AutoCloseable> registration = new AtomicReference<>();
        AtomicReference<AutoCloseable> heartbeatRegistration = new AtomicReference<>();
        sink.onClose(() -> {
            closeQuietly(registration.get());
            closeQuietly(heartbeatRegistration.get());
        });

        WorkspacePageChangeSubscription handle = subscriptions.subscribe(pageId, signal -> {
            if (signal == null || sink.isClosed()) {
                closeConnection(sink, registration, heartbeatRegistration);
                return;
            }
            OutboundSseEvent event = sse.newEventBuilder()
                    .id(Long.toString(signal.revision()))
                    .name("page-change")
                    .mediaType(MediaType.APPLICATION_JSON_TYPE)
                    .data(ChangeEvent.class, ChangeEvent.from(signal))
                    .build();
            // A page change arriving while another frame is still being written means this
            // consumer is behind. Disconnect and recover from the M35 durable revision feed.
            send(event, sink, sending, registration, heartbeatRegistration, true);
        });
        registration.set(handle);
        handle.onRevoked(sink::close);

        if (!sink.isClosed()) {
            try {
                AutoCloseable heartbeat = heartbeats.schedule(() -> {
                    if (sink.isClosed()) {
                        closeQuietly(heartbeatRegistration.get());
                        return;
                    }
                    // Heartbeats are best-effort and skipped while a data frame is in flight.
                    // A comment frame keeps proxies alive without dispatching a browser event.
                    try {
                        send(sse.newEventBuilder().comment("keep-alive").build(), sink, sending,
                                registration, heartbeatRegistration, false);
                    } catch (RuntimeException heartbeatFailure) {
                        closeConnection(sink, registration, heartbeatRegistration);
                    }
                });
                heartbeatRegistration.set(heartbeat);
            } catch (RuntimeException schedulerUnavailable) {
                closeConnection(sink, registration, heartbeatRegistration);
            }
        }
        if (sink.isClosed()) closeConnection(sink, registration, heartbeatRegistration);
    }

    private static void send(OutboundSseEvent event, SseEventSink sink, AtomicBoolean sending,
            AtomicReference<AutoCloseable> registration, AtomicReference<AutoCloseable> heartbeatRegistration,
            boolean closeWhenBusy) {
        if (sink.isClosed()) {
            closeConnection(sink, registration, heartbeatRegistration);
            return;
        }
        if (!sending.compareAndSet(false, true)) {
            if (closeWhenBusy) closeConnection(sink, registration, heartbeatRegistration);
            return;
        }
        try {
            sink.send(event).whenComplete((ignored, failure) -> {
                sending.set(false);
                if (failure != null) closeConnection(sink, registration, heartbeatRegistration);
            });
        } catch (RuntimeException sendFailure) {
            sending.set(false);
            closeConnection(sink, registration, heartbeatRegistration);
        }
    }

    private static void closeConnection(SseEventSink sink, AtomicReference<AutoCloseable> registration,
            AtomicReference<AutoCloseable> heartbeatRegistration) {
        if (!sink.isClosed()) sink.close();
        closeQuietly(registration.get());
        closeQuietly(heartbeatRegistration.get());
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
