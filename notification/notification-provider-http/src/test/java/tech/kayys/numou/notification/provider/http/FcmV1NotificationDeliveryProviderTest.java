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

class FcmV1NotificationDeliveryProviderTest {
    private HttpServer server;

    @AfterEach void stopServer() { if (server != null) server.stop(0); }

    @Test void sendsTenantScopedRequestWithBearerTokenAndNoCredentialLeakage() throws Exception {
        AtomicReference<String> path = new AtomicReference<>();
        AtomicReference<String> authorization = new AtomicReference<>();
        AtomicReference<String> body = new AtomicReference<>();
        startServer(200, "{\"name\":\"projects/demo/messages/123\"}", exchange -> {
            path.set(exchange.getRequestURI().getPath());
            authorization.set(exchange.getRequestHeaders().getFirst("Authorization"));
            try {
                body.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            } catch (java.io.IOException failure) {
                throw new java.io.UncheckedIOException(failure);
            }
        });
        UUID tenant = UUID.randomUUID();
        var provider = provider(tenant, "project-a", "access-secret");
        var result = provider.deliver(request(tenant));

        assertTrue(result.accepted());
        assertEquals("/v1/projects/project-a/messages:send", path.get());
        assertEquals("Bearer access-secret", authorization.get());
        JsonNode json = new ObjectMapper().readTree(body.get());
        assertEquals("device-token", json.path("message").path("token").asText());
        assertEquals("Order updated", json.path("message").path("notification").path("title").asText());
        assertFalse(result.toString().contains("access-secret"));
    }

    @Test void resolvesCredentialsUsingTheRequestTenant() throws Exception {
        startServer(200, "{}", null);
        UUID tenant = UUID.randomUUID();
        AtomicReference<UUID> resolvedTenant = new AtomicReference<>();
        var provider = new FcmV1NotificationDeliveryProvider(
                HttpClient.newHttpClient(), new ObjectMapper(),
                id -> { resolvedTenant.set(id); return credentials("project-a", "token"); },
                baseUri(), Duration.ofSeconds(2), Clock.fixed(Instant.parse("2026-01-01T00:00:00Z"), ZoneOffset.UTC));
        assertTrue(provider.deliver(request(tenant)).accepted());
        assertEquals(tenant, resolvedTenant.get());
    }

    @Test void mapsTransientAndPermanentProviderResponsesToSafeCodes() throws Exception {
        startServer(429, "{}", null);
        assertEquals("provider_upstream_unavailable", provider(UUID.randomUUID(), "p", "t")
                .deliver(request(UUID.randomUUID())).safeErrorCode());
        stopServer();
        startServer(403, "{}", null);
        assertEquals("provider_auth_rejected", provider(UUID.randomUUID(), "p", "t")
                .deliver(request(UUID.randomUUID())).safeErrorCode());
    }

    @Test void onlyExplicitUnregisteredResponseMarksDeviceAsUnregistered() throws Exception {
        startServer(404, "{\"error\":{\"status\":\"NOT_FOUND\"}}", null);
        var result = provider(UUID.randomUUID(), "p", "t").deliver(request(UUID.randomUUID()));
        assertEquals("provider_device_unregistered", result.safeErrorCode());
    }

    @Test void rejectsExpiredTenantCredentialsWithoutCallingProvider() throws Exception {
        startServer(200, "{}", null);
        UUID tenant = UUID.randomUUID();
        var provider = new FcmV1NotificationDeliveryProvider(
                HttpClient.newHttpClient(), new ObjectMapper(),
                id -> new FcmTenantCredentialResolver.FcmTenantCredentials("p", "expired",
                        Instant.parse("2025-01-01T00:00:00Z")),
                baseUri(), Duration.ofSeconds(2), Clock.fixed(Instant.parse("2026-01-01T00:00:00Z"), ZoneOffset.UTC));
        assertEquals("provider_credentials_expired", provider.deliver(request(tenant)).safeErrorCode());
    }

    private FcmV1NotificationDeliveryProvider provider(UUID tenant, String project, String token) {
        return new FcmV1NotificationDeliveryProvider(HttpClient.newHttpClient(), new ObjectMapper(),
                id -> credentials(project, token), baseUri(), Duration.ofSeconds(2),
                Clock.fixed(Instant.parse("2026-01-01T00:00:00Z"), ZoneOffset.UTC));
    }

    private static FcmTenantCredentialResolver.FcmTenantCredentials credentials(String project, String token) {
        return new FcmTenantCredentialResolver.FcmTenantCredentials(
                project, token, Instant.parse("2027-01-01T00:00:00Z"));
    }

    private static NotificationDeliveryProvider.DeliveryRequest request(UUID tenant) {
        return new NotificationDeliveryProvider.DeliveryRequest(tenant, UUID.randomUUID(), UUID.randomUUID(),
                "device-token", "Order updated", "Your order has shipped", "order:123");
    }

    private URI baseUri() { return URI.create("http://localhost:" + server.getAddress().getPort()); }

    private void startServer(int status, String response, java.util.function.Consumer<com.sun.net.httpserver.HttpExchange> inspect)
            throws Exception {
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
