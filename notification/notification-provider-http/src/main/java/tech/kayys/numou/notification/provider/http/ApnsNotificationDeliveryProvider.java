package tech.kayys.numou.notification.provider.http;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import tech.kayys.numou.notification.application.port.NotificationDeliveryProvider;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/** Apple Push Notification service HTTP/2 provider adapter. */
public final class ApnsNotificationDeliveryProvider implements NotificationDeliveryProvider {
    private static final String PROVIDER_CODE = "apns";
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final ApnsTenantCredentialResolver credentials;
    private final URI apiBaseUri;
    private final Duration requestTimeout;
    private final Clock clock;

    public ApnsNotificationDeliveryProvider(
            HttpClient httpClient,
            ObjectMapper objectMapper,
            ApnsTenantCredentialResolver credentials,
            URI apiBaseUri,
            Duration requestTimeout,
            Clock clock) {
        this.httpClient = Objects.requireNonNull(httpClient, "httpClient is required");
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper is required");
        this.credentials = Objects.requireNonNull(credentials, "credentials is required");
        this.apiBaseUri = Objects.requireNonNull(apiBaseUri, "apiBaseUri is required");
        this.requestTimeout = positive(requestTimeout);
        this.clock = Objects.requireNonNull(clock, "clock is required");
        if (!"https".equalsIgnoreCase(apiBaseUri.getScheme()) && !"http".equalsIgnoreCase(apiBaseUri.getScheme())) {
            throw new IllegalArgumentException("APNs API URI must use HTTP or HTTPS");
        }
        if (apiBaseUri.getHost() == null) throw new IllegalArgumentException("APNs API host is required");
    }

    public static ApnsNotificationDeliveryProvider production(
            ApnsTenantCredentialResolver credentials, Duration timeout) {
        return new ApnsNotificationDeliveryProvider(HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_2).connectTimeout(timeout).build(),
                new ObjectMapper(), credentials, URI.create("https://api.push.apple.com"), timeout, Clock.systemUTC());
    }

    @Override public String providerCode() { return PROVIDER_CODE; }

    @Override
    public DeliveryResult deliver(DeliveryRequest request) {
        Objects.requireNonNull(request, "request is required");
        final ApnsTenantCredentialResolver.ApnsTenantCredentials tenantCredentials;
        try {
            tenantCredentials = Objects.requireNonNull(credentials.resolve(request.tenantId()),
                    "tenant APNs credentials are required");
        } catch (RuntimeException failure) {
            return DeliveryResult.retryable("provider_credentials_unavailable");
        }
        if (!tenantCredentials.expiresAt().isAfter(Instant.now(clock).plusSeconds(30))) {
            return DeliveryResult.retryable("provider_credentials_expired");
        }
        if (request.deviceToken().length() > 512 || request.deviceToken().contains("/")) {
            return DeliveryResult.permanentFailure("provider_device_token_invalid");
        }

        try {
            byte[] payload = objectMapper.writeValueAsBytes(buildPayload(request));
            URI endpoint = apiBaseUri.resolve("/3/device/" + request.deviceToken());
            HttpRequest httpRequest = HttpRequest.newBuilder(endpoint)
                    .timeout(requestTimeout)
                    .header("authorization", "bearer " + tenantCredentials.bearerToken())
                    .header("apns-topic", tenantCredentials.topic())
                    .header("apns-push-type", "alert")
                    .header("apns-priority", "10")
                    .header("content-type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofByteArray(payload))
                    .build();
            HttpResponse<String> response = httpClient.send(httpRequest,
                    HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            return classify(response.statusCode(), response.body());
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            return DeliveryResult.retryable("provider_request_interrupted");
        } catch (IOException | RuntimeException failure) {
            return DeliveryResult.retryable("provider_transport_error");
        }
    }

    private Map<String, Object> buildPayload(DeliveryRequest request) {
        Map<String, Object> alert = Map.of("title", request.title(), "body", request.body());
        Map<String, Object> aps = Map.of("alert", alert, "sound", "default");
        Map<String, Object> payload = new HashMap<>();
        payload.put("aps", aps);
        if (request.sourceReference() != null && !request.sourceReference().isBlank()) {
            payload.put("source_reference", request.sourceReference());
        }
        return payload;
    }

    private DeliveryResult classify(int status, String body) {
        if (status >= 200 && status < 300) return DeliveryResult.success();
        String reason = reason(body);
        if (status == 410 || "Unregistered".equals(reason) || "BadDeviceToken".equals(reason)
                || "DeviceTokenNotForTopic".equals(reason)) {
            return DeliveryResult.permanentFailure("provider_device_unregistered");
        }
        if (status == 408 || status == 425 || status == 429 || status >= 500) {
            return DeliveryResult.retryable("provider_upstream_unavailable");
        }
        if (status == 401 || status == 403) return DeliveryResult.permanentFailure("provider_auth_rejected");
        if (status >= 400 && status < 500) return DeliveryResult.permanentFailure("provider_request_rejected");
        return DeliveryResult.retryable("provider_unexpected_response");
    }

    private String reason(String body) {
        if (body == null || body.isBlank()) return "";
        try {
            JsonNode root = objectMapper.readTree(body);
            return root.path("reason").asText("");
        } catch (IOException ignored) {
            return "";
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
