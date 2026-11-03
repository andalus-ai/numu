package tech.kayys.numou.workspace.adapter.rest;

import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.HeaderParam;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.sse.OutboundSseEvent;
import jakarta.ws.rs.sse.Sse;
import jakarta.ws.rs.sse.SseEventSink;
import tech.kayys.numou.workspace.application.WorkspacePageChangeSubscriptionApi;
import tech.kayys.numou.workspace.application.port.WorkspacePageChangeSignal;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Optional SSE transport. The host must compose a tenant-aware subscription port; this resource
 * does not create an in-memory or single-instance production broker.
 */
@Path("/api/v1/pages/{pageId}/events")
public class WorkspacePageChangeSseResource {
    private final WorkspacePageChangeSubscriptionApi subscriptions;

    @Inject
    public WorkspacePageChangeSseResource(WorkspacePageChangeSubscriptionApi subscriptions) {
        this.subscriptions = subscriptions;
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
        sink.onClose(() -> closeQuietly(registration.get()));

        AutoCloseable handle = subscriptions.subscribe(pageId, signal -> {
            if (sink.isClosed()) {
                closeQuietly(registration.get());
                return;
            }
            // One outstanding write per connection. A slow consumer is disconnected rather
            // than allowing an unbounded queue of SSE frames to accumulate.
            if (!sending.compareAndSet(false, true)) {
                sink.close();
                closeQuietly(registration.get());
                return;
            }
            OutboundSseEvent event = sse.newEventBuilder()
                    .id(Long.toString(signal.revision()))
                    .name("page-change")
                    .mediaType(MediaType.APPLICATION_JSON_TYPE)
                    .data(ChangeEvent.class, ChangeEvent.from(signal))
                    .build();
            sink.send(event).whenComplete((ignored, failure) -> {
                sending.set(false);
                if (failure != null) {
                    sink.close();
                    closeQuietly(registration.get());
                }
            });
        });
        registration.set(handle);
        if (sink.isClosed()) closeQuietly(registration.get());
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
