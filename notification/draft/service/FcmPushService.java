package id.mindo.notification.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.auth.oauth2.AccessToken;
import com.google.auth.oauth2.GoogleCredentials;
import jakarta.enterprise.context.ApplicationScoped;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;
import id.mindo.notification.dto.NotificationMessage;
import id.mindo.notification.dto.PushDeviceRecord;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@ApplicationScoped
public class FcmPushService {

    private static final Logger LOG = Logger.getLogger(FcmPushService.class);
    private static final List<String> FCM_SCOPES = List.of("https://www.googleapis.com/auth/firebase.messaging");
    private static final String ANDROID_INCOMING_CALL_CHANNEL_ID = "incoming_calls_ring_v1";
    private static final String ANDROID_INCOMING_CALL_SOUND = "incoming_call";
    private static final String ANDROID_GENERAL_CHANNEL_ID = "general_notifications";

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final HttpClient httpClient = HttpClient.newHttpClient();

    @ConfigProperty(name = "mindo.push.fcm.project-id")
    java.util.Optional<String> projectId;

    @ConfigProperty(name = "mindo.push.fcm.credentials-file")
    java.util.Optional<String> credentialsFile;

    @ConfigProperty(name = "mindo.push.fcm.credentials-json")
    java.util.Optional<String> credentialsJson;

    @ConfigProperty(name = "mindo.push.fcm.send-timeout", defaultValue = "5S")
    Duration sendTimeout;

    private volatile GoogleCredentials credentials;
    private volatile Instant accessTokenExpiry;
    private volatile String accessTokenValue;

    public boolean isConfigured() {
        return projectId.isPresent() &&
                !projectId.get().trim().isEmpty() &&
                (hasNonBlank(credentialsFile) || hasNonBlank(credentialsJson));
    }

    public FcmPushResult sendToDevice(PushDeviceRecord device, NotificationMessage notification) {
        if (!isConfigured()) {
            return FcmPushResult.FAILED_RETRYABLE;
        }
        try {
            final String bearer = getAccessToken();
            final String body = objectMapper.writeValueAsString(buildMessagePayload(device, notification));
            final HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(
                            "https://fcm.googleapis.com/v1/projects/" + projectId.get().trim() + "/messages:send"))
                    .timeout(sendTimeout)
                    .header("Authorization", "Bearer " + bearer)
                    .header("Content-Type", "application/json; charset=utf-8")
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .build();
            final HttpResponse<String> response = httpClient.send(
                    request,
                    HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.statusCode() >= 200 && response.statusCode() < 300) {
                return FcmPushResult.SENT;
            }
            if (isInvalidTokenResponse(response.statusCode(), response.body())) {
                return FcmPushResult.INVALID_TOKEN;
            }
            LOG.warnv(
                    "FCM send failed. clientId={0}, tokenSuffix={1}, status={2}, body={3}",
                    device.getClientId(),
                    suffix(device.getToken()),
                    response.statusCode(),
                    response.body());
            return FcmPushResult.FAILED_RETRYABLE;
        } catch (Exception e) {
            LOG.errorv(
                    e,
                    "FCM send error. clientId={0}, tokenSuffix={1}",
                    device.getClientId(),
                    suffix(device.getToken()));
            return FcmPushResult.FAILED_RETRYABLE;
        }
    }

    private boolean isInvalidTokenResponse(int statusCode, String body) {
        final String normalized = body == null ? "" : body.toLowerCase();
        if (normalized.contains("unregistered")) {
            return true;
        }
        if (statusCode == 400 && normalized.contains("registration token")) {
            return true;
        }
        return false;
    }

    private synchronized String getAccessToken() throws IOException {
        final Instant now = Instant.now();
        if (accessTokenValue != null && accessTokenExpiry != null && accessTokenExpiry.isAfter(now.plusSeconds(30))) {
            return accessTokenValue;
        }
        final GoogleCredentials currentCredentials = getCredentials();
        currentCredentials.refreshIfExpired();
        final AccessToken token = currentCredentials.getAccessToken();
        if (token == null || token.getTokenValue() == null || token.getTokenValue().isBlank()) {
            throw new IOException("FCM access token is unavailable");
        }
        accessTokenValue = token.getTokenValue();
        accessTokenExpiry = token.getExpirationTime() == null
                ? now.plusSeconds(300)
                : token.getExpirationTime().toInstant();
        return accessTokenValue;
    }

    private synchronized GoogleCredentials getCredentials() throws IOException {
        if (credentials != null) {
            return credentials;
        }
        try (InputStream stream = openCredentialsStream()) {
            credentials = GoogleCredentials.fromStream(stream).createScoped(FCM_SCOPES);
            return credentials;
        }
    }

    private InputStream openCredentialsStream() throws IOException {
        if (hasNonBlank(credentialsJson)) {
            final String raw = credentialsJson.get().trim();
            final String decoded;
            if (raw.startsWith("{")) {
                decoded = raw;
            } else {
                decoded = new String(Base64.getDecoder().decode(raw), StandardCharsets.UTF_8);
            }
            return new ByteArrayInputStream(decoded.getBytes(StandardCharsets.UTF_8));
        }
        if (hasNonBlank(credentialsFile)) {
            return Files.newInputStream(Path.of(credentialsFile.get().trim()));
        }
        throw new IOException("Missing FCM credentials");
    }

    Map<String, Object> buildMessagePayload(PushDeviceRecord device, NotificationMessage notification) {
        final Map<String, String> data = new HashMap<>();
        final String notificationTimestamp = normalize(notification.getTimestamp(), Instant.now().toString());
        data.put("kind", notificationType(notification));
        data.put("title", safeDataTitle(notification));
        data.put("message", safeDataBody(notification));
        data.put("timestamp", notificationTimestamp);
        data.put("sent_at", notificationTimestamp);
        if (notification.getData() != null) {
            for (Map.Entry<String, Object> entry : notification.getData().entrySet()) {
                if (entry.getValue() != null) {
                    final String sanitizedKey = sanitizeDataKey(entry.getKey());
                    if (!sanitizedKey.isEmpty() && !shouldDropDataKeyForPrivacy(notification, sanitizedKey)) {
                        data.put(sanitizedKey, String.valueOf(entry.getValue()));
                    }
                }
            }
        }

        final Map<String, Object> androidNode = buildAndroidNode(notification);

        final Map<String, Object> apnsNode = Map.of(
                "headers", Map.of(
                        "apns-priority", isCallLike(notification) ? "10" : "5"),
                "payload", Map.of(
                        "aps", Map.of(
                                "sound", "default",
                                "content-available", 1)));

        final Map<String, Object> message = new HashMap<>();
        message.put("token", device.getToken());
        message.put("data", data);
        message.put("android", androidNode);
        message.put("apns", apnsNode);
        if (shouldUseSystemNotification(notification)) {
            message.put(
                    "notification",
                    Map.of(
                            "title", systemNotificationTitle(notification),
                            "body", systemNotificationBody(notification)));
        }

        return Map.of("message", message);
    }

    private Map<String, Object> buildAndroidNode(NotificationMessage notification) {
        final Map<String, Object> androidNode = new HashMap<>();
        androidNode.put("priority", "high");
        androidNode.put("notification", buildAndroidNotificationNode(notification));
        if (isCallLike(notification)) {
            androidNode.put("collapse_key", callNotificationTag(notification));
            androidNode.put("ttl", "45s");
            return androidNode;
        }
        return androidNode;
    }

    private Map<String, Object> buildAndroidNotificationNode(NotificationMessage notification) {
        final Map<String, Object> androidNotification = new HashMap<>();
        androidNotification.put("channel_id", androidChannelId(notification));
        androidNotification.put("sound", androidSound(notification));
        androidNotification.put("click_action", "FLUTTER_NOTIFICATION_CLICK");
        if (isCallLike(notification)) {
            androidNotification.put("tag", callNotificationTag(notification));
        }
        return androidNotification;
    }

    private String androidChannelId(NotificationMessage notification) {
        return isIncomingCall(notification) ? ANDROID_INCOMING_CALL_CHANNEL_ID : ANDROID_GENERAL_CHANNEL_ID;
    }

    private String androidSound(NotificationMessage notification) {
        return isIncomingCall(notification) ? ANDROID_INCOMING_CALL_SOUND : "default";
    }

    private boolean isCallLike(NotificationMessage notification) {
        final String type = normalize(notification.getType(), "").toLowerCase();
        return type.contains("call");
    }

    private boolean isIncomingCall(NotificationMessage notification) {
        return "incoming_call".equals(normalize(notification.getType(), "").toLowerCase());
    }

    private boolean isMissedCall(NotificationMessage notification) {
        return "call_ended".equals(normalize(notification.getType(), "").toLowerCase());
    }

    private boolean shouldUseSystemNotification(NotificationMessage notification) {
        final String type = normalize(notification.getType(), "").toLowerCase();
        // Incoming call pushes must be OS-visible. A data-only FCM can be accepted by
        // Firebase but still not wake a killed/background-restricted Flutter isolate.
        return !type.contains("call") || isIncomingCall(notification) || isMissedCall(notification);
    }

    private String systemNotificationTitle(NotificationMessage notification) {
        if (isChatMessage(notification)) {
            return "New message";
        }
        if (isMissedCall(notification)) {
            return "Missed call";
        }
        if (isIncomingCall(notification)) {
            return "Incoming call";
        }
        return normalize(notification.getTitle(), "Notification");
    }

    private String systemNotificationBody(NotificationMessage notification) {
        if (isChatMessage(notification)) {
            return "Open Mindo to read.";
        }
        if (isMissedCall(notification)) {
            return "You missed a call";
        }
        if (isIncomingCall(notification)) {
            return "Tap to answer.";
        }
        return normalize(notification.getMessage(), "");
    }

    private String callNotificationTag(NotificationMessage notification) {
        final String callId = dataString(notification, "call_id", "callId");
        if (!callId.isEmpty()) {
            return "call_" + callId;
        }
        final String roomId = dataString(notification, "room_id", "roomId", "room");
        if (!roomId.isEmpty()) {
            return "call_room_" + roomId;
        }
        final String toUserId = dataString(notification, "to_user_id", "toUserId");
        return toUserId.isEmpty() ? "call" : "call_" + toUserId;
    }

    private String dataString(NotificationMessage notification, String... keys) {
        final Map<String, Object> data = notification.getData();
        if (data == null || keys == null) {
            return "";
        }
        for (String key : keys) {
            if (key == null) {
                continue;
            }
            final Object value = data.get(key);
            if (value == null) {
                continue;
            }
            final String normalized = value.toString().trim();
            if (!normalized.isEmpty()) {
                return normalized;
            }
        }
        return "";
    }

    private String notificationType(NotificationMessage notification) {
        final String type = normalize(notification.getType(), "message").toLowerCase();
        if (!type.isEmpty()) {
            return type;
        }
        return dataString(notification, "kind", "type", "notificationType", "notification_type").toLowerCase();
    }

    private boolean isChatMessage(NotificationMessage notification) {
        final String type = notificationType(notification);
        return "message".equals(type) || "chat_message".equals(type);
    }

    private String safeDataTitle(NotificationMessage notification) {
        if (isChatMessage(notification)) {
            return "New message";
        }
        if (isIncomingCall(notification)) {
            return "Incoming call";
        }
        if (isMissedCall(notification)) {
            return "Missed call";
        }
        return normalize(notification.getTitle(), "Notification");
    }

    private String safeDataBody(NotificationMessage notification) {
        if (isChatMessage(notification)) {
            return "Open Mindo to read.";
        }
        if (isIncomingCall(notification)) {
            return "Tap to answer.";
        }
        if (isMissedCall(notification)) {
            return "You missed a call";
        }
        return normalize(notification.getMessage(), "");
    }

    private boolean shouldDropDataKeyForPrivacy(NotificationMessage notification, String sanitizedKey) {
        final String lower = sanitizedKey.toLowerCase();
        if (isChatMessage(notification)) {
            return lower.equals("title") ||
                    lower.equals("message") ||
                    lower.equals("body") ||
                    lower.equals("content") ||
                    lower.equals("text") ||
                    lower.equals("preview") ||
                    lower.equals("sendername") ||
                    lower.equals("sender_name") ||
                    lower.equals("roomname") ||
                    lower.equals("room_name") ||
                    lower.equals("fromusername") ||
                    lower.equals("from_username") ||
                    lower.equals("alias") ||
                    lower.equals("senderalias") ||
                    lower.equals("sender_alias");
        }
        if (isCallLike(notification)) {
            return lower.equals("title") ||
                    lower.equals("message") ||
                    lower.equals("body") ||
                    lower.equals("callername") ||
                    lower.equals("caller_name") ||
                    lower.equals("fromusername") ||
                    lower.equals("from_username") ||
                    lower.equals("sendername") ||
                    lower.equals("sender_name") ||
                    lower.equals("alias");
        }
        return false;
    }

    private String sanitizeDataKey(String rawKey) {
        final String key = normalize(rawKey, "");
        if (key.isEmpty()) {
            return "";
        }
        final String lower = key.toLowerCase();
        if ("message_type".equals(lower)) {
            return "chat_message_type";
        }
        if ("from".equals(lower)) {
            return "mindo_from";
        }
        if ("collapse_key".equals(lower)) {
            return "mindo_collapse_key";
        }
        if (lower.startsWith("google.") || lower.startsWith("gcm.")) {
            return "mindo_" + key.replaceAll("[^A-Za-z0-9_]", "_");
        }
        return key;
    }

    private String normalize(String value, String fallback) {
        return value == null || value.trim().isEmpty() ? fallback : value.trim();
    }

    private boolean hasNonBlank(java.util.Optional<String> value) {
        return value.isPresent() && !value.get().trim().isEmpty();
    }

    private String suffix(String token) {
        if (token == null || token.length() <= 8) {
            return token == null ? "" : token;
        }
        return token.substring(token.length() - 8);
    }

    public enum FcmPushResult {
        SENT,
        INVALID_TOKEN,
        FAILED_RETRYABLE
    }
}
