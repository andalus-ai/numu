package tech.kayys.syirkah.communication.domain.model;

import tech.kayys.syirkah.foundation.domain.entity.AbstractAggregateRoot;
import tech.kayys.syirkah.communication.domain.identifier.NotificationId;
import tech.kayys.syirkah.communication.domain.valueobject.ChannelType;
import tech.kayys.syirkah.communication.domain.valueobject.NotificationPriority;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Notification aggregate root.
 * Represents a notification sent to a user.
 */
public final class Notification extends AbstractAggregateRoot<NotificationId> {
    
    private static final long serialVersionUID = 1L;

    private String title;
    private String body;
    private String link;
    private String icon;
    private String image;
    private String color;
    private NotificationPriority priority;
    private ChannelType channel;
    private String recipientUserId;
    private String recipientEmail;
    private String recipientPhone;
    private String senderUserId;
    private Instant deliveredAt;
    private Instant readAt;
    private boolean read;
    private boolean dismissed;
    private String category;
    private String actionUrl;
    private String actionLabel;
    private List<String> buttons;
    private String metadata;
    private String notes;
    private boolean active;

    private Notification(NotificationId id) {
        super(id);
        this.priority = NotificationPriority.MEDIUM;
        this.read = false;
        this.dismissed = false;
        this.active = true;
        this.buttons = new ArrayList<>();
    }

    private Notification() {
        super();
    }

    /**
     * Factory method to create a new notification.
     */
    public static Notification create(
            NotificationId id,
            String title,
            String body,
            String recipientUserId,
            ChannelType channel) {
        Notification notification = new Notification(id);
        notification.title = title;
        notification.body = body;
        notification.recipientUserId = recipientUserId;
        notification.channel = channel;
        return notification;
    }

    /**
     * Marks the notification as delivered.
     */
    public void markDelivered() {
        this.deliveredAt = Instant.now();
        setUpdatedAt(Instant.now());
        incrementVersion();
    }

    /**
     * Marks the notification as read.
     */
    public void markRead() {
        this.read = true;
        this.readAt = Instant.now();
        setUpdatedAt(Instant.now());
        incrementVersion();
    }

    /**
     * Dismisses the notification.
     */
    public void dismiss() {
        this.dismissed = true;
        this.active = false;
        setUpdatedAt(Instant.now());
        incrementVersion();
    }

    /**
     * Sets the action URL.
     */
    public void setAction(String actionUrl, String actionLabel) {
        this.actionUrl = actionUrl;
        this.actionLabel = actionLabel;
        setUpdatedAt(Instant.now());
        incrementVersion();
    }

    /**
     * Adds a button to the notification.
     */
    public void addButton(String button) {
        buttons.add(button);
        setUpdatedAt(Instant.now());
        incrementVersion();
    }

    /**
     * Checks if the notification is still relevant.
     */
    public boolean isRelevant() {
        return active && !read && !dismissed;
    }

    // Getters
    public String getTitle() { return title; }
    public String getBody() { return body; }
    public String getLink() { return link; }
    public String getIcon() { return icon; }
    public String getImage() { return image; }
    public String getColor() { return color; }
    public NotificationPriority getPriority() { return priority; }
    public ChannelType getChannel() { return channel; }
    public String getRecipientUserId() { return recipientUserId; }
    public String getRecipientEmail() { return recipientEmail; }
    public String getRecipientPhone() { return recipientPhone; }
    public String getSenderUserId() { return senderUserId; }
    public Instant getDeliveredAt() { return deliveredAt; }
    public Instant getReadAt() { return readAt; }
    public boolean isRead() { return read; }
    public boolean isDismissed() { return dismissed; }
    public String getCategory() { return category; }
    public String getActionUrl() { return actionUrl; }
    public String getActionLabel() { return actionLabel; }
    public List<String> getButtons() { return Collections.unmodifiableList(buttons); }
    public String getMetadata() { return metadata; }
    public String getNotes() { return notes; }
    public boolean isActive() { return active; }

    public void setLink(String link) {
        this.link = link;
        setUpdatedAt(Instant.now());
        incrementVersion();
    }

    public void setIcon(String icon) {
        this.icon = icon;
        setUpdatedAt(Instant.now());
        incrementVersion();
    }

    public void setImage(String image) {
        this.image = image;
        setUpdatedAt(Instant.now());
        incrementVersion();
    }

    public void setColor(String color) {
        this.color = color;
        setUpdatedAt(Instant.now());
        incrementVersion();
    }

    public void setPriority(NotificationPriority priority) {
        this.priority = priority;
        setUpdatedAt(Instant.now());
        incrementVersion();
    }

    public void setRecipientEmail(String recipientEmail) {
        this.recipientEmail = recipientEmail;
        setUpdatedAt(Instant.now());
        incrementVersion();
    }

    public void setRecipientPhone(String recipientPhone) {
        this.recipientPhone = recipientPhone;
        setUpdatedAt(Instant.now());
        incrementVersion();
    }

    public void setSenderUserId(String senderUserId) {
        this.senderUserId = senderUserId;
        setUpdatedAt(Instant.now());
        incrementVersion();
    }

    public void setCategory(String category) {
        this.category = category;
        setUpdatedAt(Instant.now());
        incrementVersion();
    }

    public void setMetadata(String metadata) {
        this.metadata = metadata;
        setUpdatedAt(Instant.now());
        incrementVersion();
    }

    public void setNotes(String notes) {
        this.notes = notes;
        setUpdatedAt(Instant.now());
        incrementVersion();
    }

    @Override
    public String toString() {
        return "Notification{" +
                "id=" + getId() +
                ", title='" + title + '\'' +
                ", recipientUserId='" + recipientUserId + '\'' +
                ", priority=" + priority +
                ", read=" + read +
                '}';
    }
}
