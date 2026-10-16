package tech.kayys.syirkah.communication.domain.valueobject;

/**
 * Types of messages.
 */
public enum MessageType {
    EMAIL("Email"),
    SMS("SMS"),
    PUSH_NOTIFICATION("Push Notification"),
    IN_APP("In-App Notification"),
    SLACK("Slack"),
    TEAMS("Microsoft Teams"),
    WHATSAPP("WhatsApp"),
    TELEGRAM("Telegram"),
    WEBHOOK("Webhook");

    private final String displayName;

    MessageType(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }

    public boolean isRealtime() {
        return this == PUSH_NOTIFICATION || this == IN_APP || 
               this == SLACK || this == TEAMS || this == WHATSAPP;
    }

    public boolean isTrackable() {
        return this == EMAIL || this == SMS;
    }
}
