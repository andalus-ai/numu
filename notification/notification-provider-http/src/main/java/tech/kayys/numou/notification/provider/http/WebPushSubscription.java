package tech.kayys.numou.notification.provider.http;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.net.URI;
import java.util.Base64;
import java.util.Objects;

/** Validated browser Push API subscription. Its serialized form must be encrypted at rest. */
public record WebPushSubscription(URI endpoint, byte[] p256dh, byte[] auth) {
    public WebPushSubscription {
        Objects.requireNonNull(endpoint, "endpoint is required");
        Objects.requireNonNull(p256dh, "p256dh is required");
        Objects.requireNonNull(auth, "auth is required");
        if (!"https".equalsIgnoreCase(endpoint.getScheme()) || endpoint.getHost() == null
                || endpoint.getUserInfo() != null || endpoint.getFragment() != null
                ) {
            throw new IllegalArgumentException("Web Push endpoint must be an HTTPS origin URL without user info or fragment");
        }
        if (p256dh.length != 65 || p256dh[0] != 0x04) {
            throw new IllegalArgumentException("p256dh must be an uncompressed P-256 public key");
        }
        if (auth.length != 16) throw new IllegalArgumentException("auth secret must contain 16 bytes");
        p256dh = p256dh.clone();
        auth = auth.clone();
    }

    @Override public byte[] p256dh() { return p256dh.clone(); }
    @Override public byte[] auth() { return auth.clone(); }
    @Override public String toString() { return "WebPushSubscription[endpoint=[REDACTED], p256dh=[REDACTED], auth=[REDACTED]]"; }

    public static WebPushSubscription parse(String serialized, ObjectMapper mapper) {
        try {
            JsonNode root = mapper.readTree(Objects.requireNonNull(serialized, "subscription is required"));
            URI endpoint = URI.create(root.path("endpoint").asText());
            JsonNode keys = root.path("keys");
            byte[] publicKey = Base64.getUrlDecoder().decode(keys.path("p256dh").asText());
            byte[] auth = Base64.getUrlDecoder().decode(keys.path("auth").asText());
            return new WebPushSubscription(endpoint, publicKey, auth);
        } catch (RuntimeException | java.io.IOException failure) {
            throw new IllegalArgumentException("Web Push subscription is malformed", failure);
        }
    }

    public String audience() {
        int port = endpoint.getPort();
        return endpoint.getScheme().toLowerCase() + "://" + endpoint.getHost().toLowerCase()
                + (port == -1 || port == 443 ? "" : ":" + port);
    }
}
