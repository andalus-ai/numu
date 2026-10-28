package id.mindo.notification.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.websocket.*;
import jakarta.websocket.server.PathParam;
import jakarta.websocket.server.ServerEndpoint;
import id.mindo.notification.dto.NotificationMessage;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArraySet;

@ServerEndpoint("/api/notifications/ws/{clientId}")
@ApplicationScoped
public class WebSocketService {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final ConcurrentHashMap<String, Set<Session>> notificationSessions = new ConcurrentHashMap<>();

    @Inject
    NotificationAuthService notificationAuthService;

    @OnOpen
    public void onOpen(Session session, @PathParam("clientId") String clientId) {
        if (!isClientAllowed(session, clientId)) {
            closeUnauthorized(session);
            return;
        }
        addNotificationSession(clientId, session);
    }

    @OnClose
    public void onClose(Session session, @PathParam("clientId") String clientId) {
        removeNotificationSession(clientId, session);
    }

    @OnError
    public void onError(Session session, @PathParam("clientId") String clientId, Throwable throwable) {
        removeNotificationSession(clientId, session);
    }

    @OnMessage
    public void onMessage(String message, Session session, @PathParam("clientId") String clientId) {
        if (message == null || message.isBlank() || !session.isOpen()) {
            return;
        }
        try {
            Map<?, ?> payload = objectMapper.readValue(message, Map.class);
            Object type = payload.get("type");
            if (!"ping".equals(String.valueOf(type))) {
                return;
            }
            Object seq = payload.get("seq");
            Object data = payload.get("data");
            if (seq == null && data instanceof Map<?, ?> dataMap) {
                seq = dataMap.get("seq");
            }
            session.getAsyncRemote().sendText(objectMapper.writeValueAsString(Map.of(
                    "type", "pong",
                    "clientId", clientId,
                    "seq", seq == null ? "" : seq.toString(),
                    "ts", System.currentTimeMillis())));
        } catch (Exception ignored) {
            // Ignore malformed client-side keepalive frames.
        }
    }

    public void addNotificationSession(String clientId, Session session) {
        notificationSessions.computeIfAbsent(clientId, k -> new CopyOnWriteArraySet<>()).add(session);
    }

    public void removeNotificationSession(String clientId, Session session) {
        Set<Session> sessions = notificationSessions.get(clientId);
        if (sessions != null) {
            sessions.remove(session);
            if (sessions.isEmpty()) {
                notificationSessions.remove(clientId);
            }
        }
    }

    public void sendNotification(String clientId, NotificationMessage notification) {
        try {
            String message = objectMapper.writeValueAsString(notification);
            Set<Session> sessions = notificationSessions.get(clientId);
            if (sessions != null) {
                for (Session session : sessions) {
                    if (session.isOpen()) {
                        session.getAsyncRemote().sendText(message);
                    }
                }
            }
        } catch (Exception e) {
            System.err.println("Failed to send notification: " + e.getMessage());
        }
    }

    public void broadcastNotification(NotificationMessage notification) {
        try {
            String message = objectMapper.writeValueAsString(notification);
            notificationSessions.values().forEach(sessions -> {
                sessions.forEach(session -> {
                    if (session.isOpen()) {
                        session.getAsyncRemote().sendText(message);
                    }
                });
            });
        } catch (Exception e) {
            System.err.println("Failed to broadcast notification: " + e.getMessage());
        }
    }

    private boolean isClientAllowed(Session session, String clientId) {
        if (notificationAuthService == null || !notificationAuthService.requiresDeviceAuth()) {
            return true;
        }
        final String token = firstQueryParam(session, "token");
        if (token.isBlank()) {
            return false;
        }
        return notificationAuthService
                .principalFromAuthorization("Bearer " + token)
                .map(principal -> notificationAuthService.principalCanAccessClient(principal, clientId))
                .orElse(false);
    }

    private String firstQueryParam(Session session, String key) {
        final List<String> values = session.getRequestParameterMap().get(key);
        if (values == null || values.isEmpty() || values.get(0) == null) {
            return "";
        }
        return values.get(0).trim();
    }

    private void closeUnauthorized(Session session) {
        try {
            session.close(new CloseReason(
                    CloseReason.CloseCodes.VIOLATED_POLICY,
                    "Unauthorized notification websocket"));
        } catch (IOException ignored) {
        }
    }
}
