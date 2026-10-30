package tech.kayys.numou.notification.provider.http;

import com.fasterxml.jackson.databind.ObjectMapper;
import tech.kayys.numou.notification.application.port.NotificationDeliveryProvider;

import java.io.IOException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.Signature;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Map;
import java.util.Objects;

/** Standards-based Web Push adapter using RFC 8291 encryption and VAPID ES256. */
public final class WebPushNotificationDeliveryProvider implements NotificationDeliveryProvider {
    private static final String PROVIDER_CODE = "webpush";
    private static final Duration MAX_VAPID_LIFETIME = Duration.ofHours(12);
    private static final int MAX_RECORD_BYTES = 4096;
    private final HttpClient client;
    private final ObjectMapper mapper;
    private final WebPushVapidCredentialResolver credentials;
    private final WebPushEndpointPolicy endpointPolicy;
    private final WebPushPayloadEncryptor encryptor;
    private final Duration timeout;
    private final Clock clock;

    public WebPushNotificationDeliveryProvider(HttpClient client, ObjectMapper mapper,
            WebPushVapidCredentialResolver credentials, WebPushEndpointPolicy endpointPolicy,
            Duration timeout, Clock clock) {
        this.client = Objects.requireNonNull(client, "client is required");
        this.mapper = Objects.requireNonNull(mapper, "mapper is required");
        this.credentials = Objects.requireNonNull(credentials, "credentials is required");
        this.endpointPolicy = Objects.requireNonNull(endpointPolicy, "endpointPolicy is required");
        this.timeout = positive(timeout);
        this.clock = Objects.requireNonNull(clock, "clock is required");
        this.encryptor = new WebPushPayloadEncryptor();
        if (client.followRedirects() != HttpClient.Redirect.NEVER)
            throw new IllegalArgumentException("Web Push HTTP client must not follow redirects");
    }

    /**
     * Creates a production adapter around a host-configured egress client.
     * The host client must route through its SSRF-safe egress proxy or equivalent
     * connection-pinned policy and must not follow redirects.
     */
    public static WebPushNotificationDeliveryProvider production(HttpClient egressClient,
            WebPushVapidCredentialResolver credentials, WebPushEndpointPolicy endpointPolicy,
            Duration timeout, Clock clock) {
        return new WebPushNotificationDeliveryProvider(egressClient, new ObjectMapper(), credentials,
                endpointPolicy, timeout, clock);
    }

    @Override public String providerCode() { return PROVIDER_CODE; }

    @Override public DeliveryResult deliver(DeliveryRequest request) {
        Objects.requireNonNull(request, "request is required");
        final WebPushSubscription subscription;
        try {
            subscription = WebPushSubscription.parse(request.deviceToken(), mapper);
        } catch (RuntimeException invalid) {
            return DeliveryResult.permanentFailure("provider_subscription_invalid");
        }
        try {
            if (!endpointPolicy.isAllowed(request.tenantId(), subscription.endpoint()))
                return DeliveryResult.permanentFailure("provider_endpoint_rejected");
        } catch (RuntimeException policyFailure) {
            return DeliveryResult.permanentFailure("provider_endpoint_rejected");
        }

        final WebPushVapidCredentialResolver.VapidCredentials tenantCredentials;
        try {
            tenantCredentials = Objects.requireNonNull(credentials.resolve(request.tenantId()),
                    "tenant VAPID credentials are required");
        } catch (RuntimeException unavailable) {
            return DeliveryResult.retryable("provider_credentials_unavailable");
        }
        Instant now = Instant.now(clock);
        if (!tenantCredentials.expiresAt().isAfter(now.plusSeconds(30)))
            return DeliveryResult.retryable("provider_credentials_expired");

        try {
            byte[] payload = mapper.writeValueAsBytes(Map.of(
                    "title", request.title(), "body", request.body(),
                    "source_reference", request.sourceReference() == null ? "" : request.sourceReference()));
            byte[] encrypted = encryptor.encrypt(subscription, payload);
            if (encrypted.length - 86 > MAX_RECORD_BYTES)
                return DeliveryResult.permanentFailure("provider_payload_too_large");
            String jwt = vapidToken(subscription.audience(), tenantCredentials, now);
            HttpRequest httpRequest = HttpRequest.newBuilder(subscription.endpoint())
                    .timeout(timeout)
                    .header("TTL", "60")
                    .header("Content-Encoding", "aes128gcm")
                    .header("Content-Type", "application/octet-stream")
                    .header("Authorization", "vapid t=" + jwt + ", k="
                            + Base64.getUrlEncoder().withoutPadding().encodeToString(tenantCredentials.publicKey()))
                    .POST(HttpRequest.BodyPublishers.ofByteArray(encrypted))
                    .build();
            HttpResponse<Void> response = client.send(httpRequest, HttpResponse.BodyHandlers.discarding());
            return classify(response.statusCode());
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            return DeliveryResult.retryable("provider_request_interrupted");
        } catch (IOException | GeneralSecurityException | RuntimeException failure) {
            return DeliveryResult.retryable("provider_transport_error");
        }
    }

    private String vapidToken(String audience, WebPushVapidCredentialResolver.VapidCredentials credential,
            Instant now) throws GeneralSecurityException, IOException {
        Instant expiry = now.plus(MAX_VAPID_LIFETIME);
        if (credential.expiresAt().isBefore(expiry)) expiry = credential.expiresAt();
        String header = base64Url(mapper.writeValueAsBytes(Map.of("typ", "JWT", "alg", "ES256")));
        String claims = base64Url(mapper.writeValueAsBytes(Map.of(
                "aud", audience, "exp", expiry.getEpochSecond(), "sub", credential.subject())));
        String signingInput = header + "." + claims;
        Signature signer = Signature.getInstance("SHA256withECDSA");
        signer.initSign(credential.privateKey());
        signer.update(signingInput.getBytes(StandardCharsets.US_ASCII));
        return signingInput + "." + base64Url(derToJose(signer.sign()));
    }

    private static byte[] derToJose(byte[] der) {
        int offset = 0;
        if (der[offset++] != 0x30) throw new IllegalArgumentException("Invalid ECDSA signature");
        int sequenceLength = der[offset++] & 0xff;
        if (sequenceLength > 127) throw new IllegalArgumentException("Unsupported ECDSA signature encoding");
        byte[] result = new byte[64];
        for (int part = 0; part < 2; part++) {
            if (der[offset++] != 0x02) throw new IllegalArgumentException("Invalid ECDSA signature");
            int length = der[offset++] & 0xff;
            int start = offset;
            offset += length;
            while (length > 32 && der[start] == 0) { start++; length--; }
            if (length > 32) throw new IllegalArgumentException("Invalid ECDSA integer");
            System.arraycopy(der, start, result, part * 32 + 32 - length, length);
        }
        return result;
    }

    private static String base64Url(byte[] value) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(value);
    }

    private DeliveryResult classify(int status) {
        if (status >= 200 && status < 300) return DeliveryResult.success();
        if (status == 404 || status == 410)
            return DeliveryResult.permanentFailure("provider_device_unregistered");
        if (status == 408 || status == 425 || status == 429 || status >= 500)
            return DeliveryResult.retryable("provider_upstream_unavailable");
        if (status == 401 || status == 403)
            return DeliveryResult.permanentFailure("provider_auth_rejected");
        if (status >= 400 && status < 500)
            return DeliveryResult.permanentFailure("provider_request_rejected");
        return DeliveryResult.retryable("provider_unexpected_response");
    }

    private static Duration positive(Duration value) {
        Objects.requireNonNull(value, "timeout is required");
        if (value.isZero() || value.isNegative()) throw new IllegalArgumentException("timeout must be positive");
        return value;
    }
}
