package tech.kayys.numou.courier.adapter.postgres;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

class HttpCourierEventPublisherTest {
    private HttpServer server;

    @AfterEach
    void stopServer() {
        if (server != null) server.stop(0);
    }

    @Test
    void sendsJsonEnvelopeAndStableTenantScopedIdempotencyHeaders() throws Exception {
        AtomicReference<String> body = new AtomicReference<>();
        AtomicReference<String> idempotencyKey = new AtomicReference<>();
        AtomicReference<String> authorization = new AtomicReference<>();
        startServer(204, exchange -> {
            body.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            idempotencyKey.set(exchange.getRequestHeaders().getFirst("Idempotency-Key"));
            authorization.set(exchange.getRequestHeaders().getFirst("Authorization"));
        });

        CourierPublishedEvent event = event();
        publisher("deliveries", "secret-token").publish("deliveries", event);

        assertTrue(body.get().contains("\"eventType\":\"courier.shipment.delivered.v1\""));
        assertTrue(body.get().contains("\"payload\":{\"shipmentId\":\"S-1\"}"));
        assertEquals(event.tenantId() + ":" + event.eventId(), idempotencyKey.get());
        assertEquals("Bearer secret-token", authorization.get());
    }

    @Test
    void rejectsNonSuccessResponseSoRelayCanRetry() throws Exception {
        startServer(503, exchange -> exchange.getRequestBody().readAllBytes());
        HttpCourierEventPublisher publisher = publisher("default", "");

        IOException failure = assertThrows(IOException.class, () -> publisher.publish("default", event()));
        assertTrue(failure.getMessage().contains("503"));
    }

    @Test
    void refusesWrongDestinationBeforeNetworkRequest() throws Exception {
        startServer(204, exchange -> exchange.getRequestBody().readAllBytes());
        HttpCourierEventPublisher publisher = publisher("configured", "");

        assertThrows(IllegalArgumentException.class, () -> publisher.publish("other", event()));
    }

    @Test
    void endpointMustBeAbsoluteHttpOrHttpsWithoutCredentials() {
        assertThrows(IllegalArgumentException.class, () -> new HttpCourierEventPublisher(
                URI.create("ftp://example.com/events"), "default", "", 1000, 1000, HttpClient.newHttpClient()));
        assertThrows(IllegalArgumentException.class, () -> new HttpCourierEventPublisher(
                URI.create("http://user:password@example.com/events"), "default", "", 1000, 1000, HttpClient.newHttpClient()));
    }

    private HttpCourierEventPublisher publisher(String destination, String token) {
        URI endpoint = URI.create("http://127.0.0.1:" + server.getAddress().getPort() + "/events");
        return new HttpCourierEventPublisher(endpoint, destination, token, 1000, 1000,
                HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(1)).build());
    }

    private void startServer(int status, ExchangeHandler handler) throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/events", exchange -> {
            try {
                handler.handle(exchange);
                exchange.sendResponseHeaders(status, -1);
            } catch (Throwable failure) {
                exchange.close();
                throw failure;
            } finally {
                exchange.close();
            }
        });
        server.start();
    }

    private static CourierPublishedEvent event() {
        return new CourierPublishedEvent(UUID.randomUUID(), UUID.randomUUID(),
                "courier.shipment.delivered.v1", "shipment", "S-1",
                Instant.parse("2026-10-25T00:00:00Z"), "{\"shipmentId\":\"S-1\"}");
    }

    @FunctionalInterface
    private interface ExchangeHandler {
        void handle(com.sun.net.httpserver.HttpExchange exchange) throws Exception;
    }
}
