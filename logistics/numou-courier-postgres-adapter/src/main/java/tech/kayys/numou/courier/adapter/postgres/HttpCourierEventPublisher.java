package tech.kayys.numou.courier.adapter.postgres;

import io.quarkus.arc.properties.IfBuildProperty;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Objects;

/**
 * Optional HTTP event-gateway transport. Enable explicitly with
 * numou.courier.outbox.http.enabled=true. The receiver must deduplicate using
 * the Idempotency-Key (tenantId:eventId), because the relay guarantees at-least-once delivery.
 */
@ApplicationScoped
@IfBuildProperty(name = "numou.courier.outbox.http.enabled", stringValue = "true")
public class HttpCourierEventPublisher implements CourierEventPublisher {
    private final HttpClient client;
    private final URI endpoint;
    private final String expectedDestination;
    private final String bearerToken;
    private final Duration requestTimeout;

    @Inject
    public HttpCourierEventPublisher(
            @ConfigProperty(name = "numou.courier.outbox.http.url") String endpoint,
            @ConfigProperty(name = "numou.courier.outbox.http.destination", defaultValue = "default") String expectedDestination,
            @ConfigProperty(name = "numou.courier.outbox.http.bearer-token", defaultValue = "") String bearerToken,
            @ConfigProperty(name = "numou.courier.outbox.http.connect-timeout-ms", defaultValue = "2000") long connectTimeoutMs,
            @ConfigProperty(name = "numou.courier.outbox.http.request-timeout-ms", defaultValue = "5000") long requestTimeoutMs) {
        this(validateEndpoint(endpoint), expectedDestination, bearerToken,
                connectTimeoutMs, requestTimeoutMs, HttpClient.newBuilder()
                        .connectTimeout(Duration.ofMillis(positive(connectTimeoutMs, "connect timeout")))
                        .followRedirects(HttpClient.Redirect.NEVER)
                        .build());
    }

    HttpCourierEventPublisher(URI endpoint, String expectedDestination, String bearerToken,
                              long connectTimeoutMs, long requestTimeoutMs, HttpClient client) {
        this.endpoint = validateEndpoint(Objects.requireNonNull(endpoint, "endpoint").toString());
        if (expectedDestination == null || expectedDestination.isBlank() || expectedDestination.length() > 120)
            throw new IllegalArgumentException("HTTP destination must contain 1-120 characters");
        this.expectedDestination = expectedDestination;
        this.bearerToken = bearerToken == null ? "" : bearerToken.trim();
        this.requestTimeout = Duration.ofMillis(positive(requestTimeoutMs, "request timeout"));
        positive(connectTimeoutMs, "connect timeout");
        this.client = Objects.requireNonNull(client, "client");
    }

    @Override
    public void publish(String destination, CourierPublishedEvent event) throws Exception {
        if (!expectedDestination.equals(destination))
            throw new IllegalArgumentException("HTTP transport is configured for a different destination");
        Objects.requireNonNull(event, "event");
        String body = "{"
                + "\"tenantId\":" + quote(event.tenantId().toString()) + ","
                + "\"eventId\":" + quote(event.eventId().toString()) + ","
                + "\"eventType\":" + quote(event.eventType()) + ","
                + "\"aggregateType\":" + quote(event.aggregateType()) + ","
                + "\"aggregateReference\":" + quote(event.aggregateReference()) + ","
                + "\"occurredAt\":" + quote(event.occurredAt().toString()) + ","
                + "\"payload\":" + event.payloadJson()
                + "}";

        HttpRequest.Builder request = HttpRequest.newBuilder(endpoint)
                .timeout(requestTimeout)
                .header("Content-Type", "application/json")
                .header("Accept", "application/json")
                .header("X-Numou-Tenant-Id", event.tenantId().toString())
                .header("X-Numou-Event-Id", event.eventId().toString())
                .header("X-Numou-Event-Type", event.eventType())
                .header("Idempotency-Key", event.tenantId() + ":" + event.eventId())
                .POST(HttpRequest.BodyPublishers.ofString(body));
        if (!bearerToken.isEmpty()) request.header("Authorization", "Bearer " + bearerToken);

        HttpResponse<Void> response = client.send(request.build(), HttpResponse.BodyHandlers.discarding());
        int status = response.statusCode();
        if (status < 200 || status >= 300) {
            throw new IOException("Courier HTTP destination rejected event with status " + status);
        }
    }

    private static URI validateEndpoint(String value) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException("HTTP endpoint URL is required");
        URI uri;
        try {
            uri = URI.create(value.trim());
        } catch (IllegalArgumentException invalid) {
            throw new IllegalArgumentException("HTTP endpoint URL is invalid", invalid);
        }
        String scheme = uri.getScheme();
        if (!uri.isAbsolute() || !("http".equalsIgnoreCase(scheme) || "https".equalsIgnoreCase(scheme))
                || uri.getHost() == null || uri.getUserInfo() != null || uri.getFragment() != null) {
            throw new IllegalArgumentException("HTTP endpoint must be an absolute http(s) URL without user-info or fragment");
        }
        return uri;
    }

    private static long positive(long value, String name) {
        if (value < 1 || value > 120_000) throw new IllegalArgumentException(name + " must be between 1 and 120000 ms");
        return value;
    }

    private static String quote(String value) {
        StringBuilder out = new StringBuilder(value.length() + 2).append('"');
        for (int i = 0; i < value.length(); i++) {
            char ch = value.charAt(i);
            switch (ch) {
                case '"' -> out.append("\\\"");
                case '\\' -> out.append("\\\\");
                case '\b' -> out.append("\\b");
                case '\f' -> out.append("\\f");
                case '\n' -> out.append("\\n");
                case '\r' -> out.append("\\r");
                case '\t' -> out.append("\\t");
                default -> {
                    if (ch < 0x20) out.append(String.format("\\u%04x", (int) ch));
                    else out.append(ch);
                }
            }
        }
        return out.append('"').toString();
    }
}
