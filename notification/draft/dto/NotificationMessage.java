package id.mindo.notification.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Instant;
import java.util.Map;

public class NotificationMessage {
    private String type;
    private String title;
    private String message;
    private Map<String, Object> data;

    private String timestamp;

    @JsonProperty("status")
    private ConnectionStatus status;

    public NotificationMessage() {
        this.timestamp = Instant.now().toString();
        this.status = ConnectionStatus.connected;
    }

    public NotificationMessage(String type, String title, String message, Map<String, Object> data) {
        this.type = type;
        this.title = title;
        this.message = message;
        this.data = data;
        this.timestamp = Instant.now().toString();
        this.status = ConnectionStatus.connected;
    }

    // Getters and Setters
    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public Map<String, Object> getData() {
        return data;
    }

    public void setData(Map<String, Object> data) {
        this.data = data;
    }

    public String getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(String timestamp) {
        this.timestamp = timestamp;
    }

    public ConnectionStatus getStatus() {
        return status;
    }

    public void setStatus(ConnectionStatus status) {
        this.status = status;
    }

    public enum ConnectionStatus {
        connected,
        connecting,
        disconnected,
        error
    }
}
