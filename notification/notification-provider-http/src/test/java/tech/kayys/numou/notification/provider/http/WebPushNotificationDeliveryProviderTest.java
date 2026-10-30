package tech.kayys.numou.notification.provider.http;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import tech.kayys.numou.notification.application.port.NotificationDeliveryProvider;

import java.net.URI;
import java.net.http.HttpClient;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.ECPublicKey;
import java.security.spec.ECGenParameterSpec;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;

class WebPushNotificationDeliveryProviderTest {
    @Test void parsesSubscriptionAndPreservesEndpointAudience() throws Exception {
        KeyPair receiver = keyPair();
        String encoded = subscriptionJson(receiver);
        WebPushSubscription subscription = WebPushSubscription.parse(encoded, new ObjectMapper());
        assertEquals("https://push.example.test", subscription.audience());
        assertEquals("WebPushSubscription[endpoint=[REDACTED], p256dh=[REDACTED], auth=[REDACTED]]",
                subscription.toString());
        assertThrows(IllegalArgumentException.class, () -> WebPushSubscription.parse(
                encoded.replace("https://push.example.test", "http://push.example.test"), new ObjectMapper()));
    }

    @Test void encryptsSingleRecordUsingRfc8291Framing() throws Exception {
        WebPushSubscription subscription = subscription(keyPair());
        byte[] encrypted = new WebPushPayloadEncryptor().encrypt(subscription,
                "{\"title\":\"hello\"}".getBytes(java.nio.charset.StandardCharsets.UTF_8));
        assertTrue(encrypted.length > 16 + 4 + 1 + 65 + 16);
        assertEquals(4096, java.nio.ByteBuffer.wrap(encrypted, 16, 4).getInt());
        assertEquals(65, encrypted[20] & 0xff);
        assertEquals(4, encrypted[21] & 0xff);
        assertFalse(java.util.Arrays.equals(encrypted,
                new WebPushPayloadEncryptor().encrypt(subscription,
                        "{\"title\":\"hello\"}".getBytes(java.nio.charset.StandardCharsets.UTF_8))));
    }

    @Test void rejectsEndpointBeforeResolvingVapidCredentials() throws Exception {
        UUID tenant = UUID.randomUUID();
        AtomicBoolean resolved = new AtomicBoolean();
        WebPushNotificationDeliveryProvider provider = new WebPushNotificationDeliveryProvider(
                HttpClient.newHttpClient(), new ObjectMapper(), id -> {
                    resolved.set(true);
                    throw new IllegalStateException("must not resolve");
                }, (id, endpoint) -> false, Duration.ofSeconds(2),
                Clock.fixed(Instant.parse("2026-01-01T00:00:00Z"), ZoneOffset.UTC));
        var result = provider.deliver(new NotificationDeliveryProvider.DeliveryRequest(
                tenant, UUID.randomUUID(), UUID.randomUUID(), subscriptionJson(keyPair()),
                "Title", "Body", "ref"));
        assertEquals("provider_endpoint_rejected", result.safeErrorCode());
        assertFalse(resolved.get());
    }

    private static KeyPair keyPair() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("EC");
        generator.initialize(new ECGenParameterSpec("secp256r1"));
        return generator.generateKeyPair();
    }

    private static WebPushSubscription subscription(KeyPair pair) throws Exception {
        return new WebPushSubscription(URI.create("https://push.example.test/send/opaque"),
                encode((ECPublicKey) pair.getPublic()), new byte[16]);
    }

    private static String subscriptionJson(KeyPair pair) throws Exception {
        return new ObjectMapper().writeValueAsString(Map.of(
                "endpoint", "https://push.example.test/send/opaque?token=opaque",
                "keys", Map.of("p256dh", Base64.getUrlEncoder().withoutPadding()
                        .encodeToString(encode((ECPublicKey) pair.getPublic())),
                        "auth", Base64.getUrlEncoder().withoutPadding().encodeToString(new byte[16]))));
    }

    private static byte[] encode(ECPublicKey key) {
        byte[] result = new byte[65];
        result[0] = 4;
        put32(key.getW().getAffineX(), result, 1);
        put32(key.getW().getAffineY(), result, 33);
        return result;
    }

    private static void put32(java.math.BigInteger value, byte[] target, int offset) {
        byte[] raw = value.toByteArray();
        int start = raw.length > 32 ? raw.length - 32 : 0;
        int length = raw.length - start;
        System.arraycopy(raw, start, target, offset + 32 - length, length);
    }
}
