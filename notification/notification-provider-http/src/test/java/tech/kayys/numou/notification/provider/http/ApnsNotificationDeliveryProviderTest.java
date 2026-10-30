package tech.kayys.numou.notification.provider.http;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import tech.kayys.numou.notification.application.port.NotificationDeliveryProvider;

import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

class ApnsNotificationDeliveryProviderTest {
    private HttpServer server;

    @AfterEach void stopServer() { if (server != null) server.stop(0); }

    @Test void sendsTenantScopedAlertWithTopicAndBearerToken() throws Exception {
        AtomicReference<String> path = new AtomicReference<>();
        AtomicReference<String> authorization = new AtomicReference<>();
        AtomicReference<String> topic = new AtomicReference<>();
        AtomicReference<String> pushType = new AtomicReference<>();
        AtomicReference<String> payload = new AtomicReference<>();
        startServer(200, "", exchange -> {
            path.set(exchange.getRequestURI().getPath());
            authorization.set(exchange.getRequestHeaders().getFirst("authorization"));
            topic.set(exchange.getRequestHeaders().getFirst("apns-topic"));
            pushType.set(exchange.getRequestHeaders().getFirst("apns-push-type"));
            try {
                payload.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            } catch (java.io.IOException failure) {
                throw new java.io.UncheckedIOException(failure);
            }
        });
        UUID tenant = UUID.randomUUID();
        AtomicReference<UUID> resolvedTenant = new AtomicReference<>();
        var provider = provider(id -> {
            resolvedTenant.set(id);
            return credentials("com.example.app", "short-lived-jwt");
        });
        var result = provider.deliver(request(tenant));

        assertTrue(result.accepted());
        assertEquals(tenant, resolvedTenant.get());
        assertEquals("/3/device/device-token", path.get());
        assertEquals("bearer short-lived-jwt", authorization.get());
        assertEquals("com.example.app", topic.get());
        assertEquals("alert", pushType.get());
        JsonNode json = new ObjectMapper().readTree(payload.get());
        assertEquals("Order updated", json.path("aps").path("alert").path("title").asText());
        assertEquals("order:123", json.path("source_reference").asText());
    }

    @Test void mapsUnregisteredAndTransientResponsesSafely() throws Exception {
        startServer(410, "{\"reason\":\"Unregistered\"}", null);
        var invalid = provider(id -> credentials("com.example.app", "jwt")).deliver(request(UUID.randomUUID()));
        assertEquals("provider_device_unregistered", invalid.safeErrorCode());

        stopServer();
        startServer(429, "{}", null);
        var throttled = provider(id -> credentials("com.example.app", "jwt")).deliver(request(UUID.randomUUID()));
        assertTrue(throttled.retryable());
        assertEquals("provider_upstream_unavailable", throttled.safeErrorCode());
    }

    @Test void rejectsExpiredCredentialsBeforeMakingNetworkRequest() throws Exception {
        startServer(200, "", null);
        var provider = provider(id -> new ApnsTenantCredentialResolver.ApnsTenantCredentials(
                "com.example.app", "expired-jwt", Instant.parse("2025-01-01T00:00:00Z")));
        var result = provider.deliver(request(UUID.randomUUID()));
        assertEquals("provider_credentials_expired", result.safeErrorCode());
    }

    private ApnsNotificationDeliveryProvider provider(ApnsTenantCredentialResolver resolver) {
        return new ApnsNotificationDeliveryProvider(HttpClient.newHttpClient(), new ObjectMapper(),
                resolver, baseUri(), Duration.ofSeconds(2),
                Clock.fixed(Instant.parse("2026-01-01T00:00:00Z"), ZoneOffset.UTC));
    }

    private static ApnsTenantCredentialResolver.ApnsTenantCredentials credentials(String topic, String jwt) {
        return new ApnsTenantCredentialResolver.ApnsTenantCredentials(
                topic, jwt, Instant.parse("2027-01-01T00:00:00Z"));
    }

    private static NotificationDeliveryProvider.DeliveryRequest request(UUID tenant) {
        return new NotificationDeliveryProvider.DeliveryRequest(tenant, UUID.randomUUID(), UUID.randomUUID(),
                "device-token", "Order updated", "Your order has shipped", "order:123");
    }

    private URI baseUri() { return URI.create("http://localhost:" + server.getAddress().getPort()); }

    private void startServer(int status, String response,
            java.util.function.Consumer<com.sun.net.httpserver.HttpExchange> inspect) throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            if (inspect != null) inspect.accept(exchange);
            byte[] bytes = response.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(status, bytes.length);
            exchange.getResponseBody().write(bytes);
            exchange.close();
        });
        server.start();
    }
}
