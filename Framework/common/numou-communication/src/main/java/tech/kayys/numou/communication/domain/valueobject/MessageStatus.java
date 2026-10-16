package tech.kayys.syirkah.communication.domain.valueobject;

/**
 * Status of a message.
 */
public enum MessageStatus {
    PENDING("Pending - waiting to be sent"),
    QUEUED("Queued - in sending queue"),
    SENDING("Sending - being processed"),
    SENT("Sent - delivered to provider"),
    DELIVERED("Delivered - confirmed delivered"),
    FAILED("Failed - sending failed"),
    BOUNCED("Bounced - returned"),
    OPENED("Opened - recipient opened"),
    CLICKED("Clicked - recipient clicked link"),
    SPAM("Spam - marked as spam"),
    UNSUBSCRIBED("Unsubscribed - recipient unsubscribed");

    private final String description;

    MessageStatus(String description) {
        this.description = description;
    }

    public String getDescription() {
        return description;
    }

    public boolean isFinal() {
        return this == SENT || this == DELIVERED || this == FAILED || 
               this == BOUNCED || this == SPAM || this == UNSUBSCRIBED;
    }

    public boolean isSuccessful() {
        return this == SENT || this == DELIVERED || this == OPENED || this == CLICKED;
    }
}
