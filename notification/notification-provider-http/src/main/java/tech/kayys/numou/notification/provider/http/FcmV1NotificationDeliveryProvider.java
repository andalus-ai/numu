package tech.kayys.numou.notification.provider.http;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import tech.kayys.numou.notification.application.port.NotificationDeliveryProvider;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/** Firebase Cloud Messaging HTTP v1 adapter. No Firebase SDK or credential storage is embedded. */
public final class FcmV1NotificationDeliveryProvider implements NotificationDeliveryProvider {
    private static final String PROVIDER_CODE = "fcm";
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final FcmTenantCredentialResolver credentials;
    private final URI apiBaseUri;
    private final Duration requestTimeout;
    private final Clock clock;

    public FcmV1NotificationDeliveryProvider(
            HttpClient httpClient,
            ObjectMapper objectMapper,
            FcmTenantCredentialResolver credentials,
            URI apiBaseUri,
            Duration requestTimeout,
            Clock clock) {
        this.httpClient = Objects.requireNonNull(httpClient, "httpClient is required");
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper is required");
        this.credentials = Objects.requireNonNull(credentials, "credentials is required");
        this.apiBaseUri = Objects.requireNonNull(apiBaseUri, "apiBaseUri is required");
        this.requestTimeout = positive(requestTimeout);
        this.clock = Objects.requireNonNull(clock, "clock is required");
        if (!"https".equalsIgnoreCase(apiBaseUri.getScheme())
                && !"http".equalsIgnoreCase(apiBaseUri.getScheme())) {
            throw new IllegalArgumentException("FCM API URI must use HTTP or HTTPS");
        }
        if (apiBaseUri.getHost() == null) throw new IllegalArgumentException("FCM API host is required");
    }

    public static FcmV1NotificationDeliveryProvider production(
            FcmTenantCredentialResolver credentials, Duration requestTimeout) {
        return new FcmV1NotificationDeliveryProvider(
                HttpClient.newBuilder().connectTimeout(requestTimeout).build(),
                new ObjectMapper(), credentials, URI.create("https://fcm.googleapis.com"),
                requestTimeout, Clock.systemUTC());
    }

    @Override public String providerCode() { return PROVIDER_CODE; }

    @Override
    public DeliveryResult deliver(DeliveryRequest request) {
        Objects.requireNonNull(request, "request is required");
        final FcmTenantCredentialResolver.FcmTenantCredentials tenantCredentials;
        try {
            tenantCredentials = Objects.requireNonNull(
                    credentials.resolve(request.tenantId()), "tenant credentials are required");
        } catch (RuntimeException failure) {
            return DeliveryResult.retryable("provider_credentials_unavailable");
        }
        if (!tenantCredentials.expiresAt().isAfter(Instant.now(clock).plusSeconds(30))) {
            return DeliveryResult.retryable("provider_credentials_expired");
        }
        if (!tenantCredentials.projectId().matches("[A-Za-z0-9:._-]{1,200}")) {
            return DeliveryResult.permanentFailure("provider_configuration_invalid");
        }

        try {
            byte[] payload = objectMapper.writeValueAsBytes(buildPayload(request));
            URI endpoint = apiBaseUri.resolve("/v1/projects/" + tenantCredentials.projectId() + "/messages:send");
            HttpRequest httpRequest = HttpRequest.newBuilder(endpoint)
                    .timeout(requestTimeout)
                    .header("Authorization", "Bearer " + tenantCredentials.accessToken())
                    .header("Content-Type", "application/json; charset=utf-8")
                    .POST(HttpRequest.BodyPublishers.ofByteArray(payload))
                    .build();
            HttpResponse<String> response = httpClient.send(
                    httpRequest, HttpResponse.BodyHandlers.ofString(java.nio.charset.StandardCharsets.UTF_8));
            return classify(response.statusCode(), response.body());
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            return DeliveryResult.retryable("provider_request_interrupted");
        } catch (IOException | RuntimeException failure) {
            return DeliveryResult.retryable("provider_transport_error");
        }
    }

    private Map<String, Object> buildPayload(DeliveryRequest request) {
        Map<String, Object> notification = Map.of("title", request.title(), "body", request.body());
        Map<String, Object> message = new HashMap<>();
        message.put("token", request.deviceToken());
        message.put("notification", notification);
        if (request.sourceReference() != null && !request.sourceReference().isBlank()) {
            message.put("data", Map.of("source_reference", request.sourceReference()));
        }
        return Map.of("message", message);
    }

    private DeliveryResult classify(int status, String body) {
        if (status >= 200 && status < 300) return DeliveryResult.success();
        if (isUnregisteredToken(body)) return DeliveryResult.permanentFailure("provider_device_unregistered");
        if (status == 408 || status == 425 || status == 429 || status >= 500) {
            return DeliveryResult.retryable("provider_upstream_unavailable");
        }
        if (status == 401 || status == 403) {
            return DeliveryResult.permanentFailure("provider_auth_rejected");
        }
        if (status >= 400 && status < 500) {
            return DeliveryResult.permanentFailure("provider_request_rejected");
        }
        return DeliveryResult.retryable("provider_unexpected_response");
    }

    private boolean isUnregisteredToken(String body) {
        if (body == null || body.isBlank()) return false;
        try {
            JsonNode root = objectMapper.readTree(body);
            JsonNode error = root.path("error");
            JsonNode details = error.path("details");
            if (details.isArray()) {
                for (JsonNode detail : details) {
                    if ("UNREGISTERED".equals(detail.path("errorCode").asText())) return true;
                }
            }
            return "NOT_FOUND".equals(error.path("status").asText());
        } catch (IOException ignored) {
            return false;
        }
    }

    private static Duration positive(Duration duration) {
        Objects.requireNonNull(duration, "requestTimeout is required");
        if (duration.isNegative() || duration.isZero() || duration.toMillis() < 1) {
            throw new IllegalArgumentException("requestTimeout must be at least one millisecond");
        }
        return duration;
    }
}
