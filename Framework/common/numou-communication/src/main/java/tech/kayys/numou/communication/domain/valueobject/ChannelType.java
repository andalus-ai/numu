package tech.kayys.syirkah.communication.domain.valueobject;

/**
 * Communication channels.
 */
public enum ChannelType {
    EMAIL("Email"),
    SMS("SMS"),
    PUSH("Push Notification"),
    IN_APP("In-App"),
    SLACK("Slack"),
    TEAMS("Teams"),
    WHATSAPP("WhatsApp"),
    TELEGRAM("Telegram"),
    WEBHOOK("Webhook"),
    FAX("Fax");

    private final String displayName;

    ChannelType(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }

    public boolean isDigital() {
        return this != FAX;
    }

    public boolean isInstant() {
        return this == PUSH || this == IN_APP || this == SLACK || 
               this == TEAMS || this == WHATSAPP || this == TELEGRAM;
    }
}
