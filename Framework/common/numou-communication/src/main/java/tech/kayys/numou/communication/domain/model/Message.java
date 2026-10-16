package tech.kayys.syirkah.communication.domain.model;

import tech.kayys.syirkah.foundation.domain.entity.AbstractAggregateRoot;
import tech.kayys.syirkah.foundation.domain.valueobject.ValueObject;
import tech.kayys.syirkah.communication.domain.identifier.MessageId;
import tech.kayys.syirkah.communication.domain.valueobject.MessageStatus;
import tech.kayys.syirkah.communication.domain.valueobject.MessageType;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Message aggregate root.
 * Represents a communication message sent through the system.
 */
public final class Message extends AbstractAggregateRoot<MessageId> {
    
    private static final long serialVersionUID = 1L;
    
    private String messageId;
    private MessageType type;
    private String fromAddress;
    private List<String> toAddresses;
    private List<String> ccAddresses;
    private List<String> bccAddresses;
    private String subject;
    private String body;
    private String htmlBody;
    private String rawContent;
    private MessageStatus status;
    private String templateId;
    private Map<String, String> templateData;
    private List<String> attachments;
    private String priority;
    private String sender;
    private String replyTo;
    private boolean tracked;
    private boolean openTracking;
    private boolean clickTracking;
    private Instant sentAt;
    private Instant deliveredAt;
    private Instant openedAt;
    private List<MessageEvent> events;
    private String failureReason;
    private String notes;
    private boolean active;
    private String tenantId;
    private String userId;

    private Message(MessageId id) {
        super(id);
        this.toAddresses = new ArrayList<>();
        this.ccAddresses = new ArrayList<>();
        this.bccAddresses = new ArrayList<>();
        this.attachments = new ArrayList<>();
        this.events = new ArrayList<>();
        this.status = MessageStatus.PENDING;
        this.tracked = true;
        this.active = true;
    }

    private Message() {
        super();
    }

    /**
     * Factory method to create a new message.
     */
    public static Message create(
            MessageId id,
            MessageType type,
            String fromAddress,
            List<String> toAddresses,
            String subject,
            String body) {
        Message message = new Message(id);
        message.type = type;
        message.fromAddress = fromAddress;
        message.toAddresses = new ArrayList<>(toAddresses);
        message.subject = subject;
        message.body = body;
        return message;
    }

    /**
     * Sets the HTML body.
     */
    public void setHtmlBody(String htmlBody) {
        this.htmlBody = htmlBody;
        setUpdatedAt(Instant.now());
        incrementVersion();
    }

    /**
     * Sets CC addresses.
     */
    public void setCcAddresses(List<String> ccAddresses) {
        this.ccAddresses = new ArrayList<>(ccAddresses);
        setUpdatedAt(Instant.now());
        incrementVersion();
    }

    /**
     * Sets BCC addresses.
     */
    public void setBccAddresses(List<String> bccAddresses) {
        this.bccAddresses = new ArrayList<>(bccAddresses);
        setUpdatedAt(Instant.now());
        incrementVersion();
    }

    /**
     * Sets template information.
     */
    public void setTemplate(String templateId, Map<String, String> templateData) {
        this.templateId = templateId;
        this.templateData = templateData;
        setUpdatedAt(Instant.now());
        incrementVersion();
    }

    /**
     * Adds an attachment.
     */
    public void addAttachment(String attachment) {
        attachments.add(attachment);
        setUpdatedAt(Instant.now());
        incrementVersion();
    }

    /**
     * Sends the message.
     */
    public void send() {
        if (status != MessageStatus.PENDING && status != MessageStatus.QUEUED) {
            throw new IllegalStateException("Cannot send message in status: " + status);
        }
        this.status = MessageStatus.SENDING;
        this.sentAt = Instant.now();
        setUpdatedAt(Instant.now());
        incrementVersion();
    }

    /**
     * Marks the message as sent.
     */
    public void markSent() {
        if (status != MessageStatus.SENDING) {
            throw new IllegalStateException("Cannot mark sent in status: " + status);
        }
        this.status = MessageStatus.SENT;
        setUpdatedAt(Instant.now());
        incrementVersion();
    }

    /**
     * Marks the message as delivered.
     */
    public void markDelivered() {
        if (status != MessageStatus.SENT) {
            throw new IllegalStateException("Cannot mark delivered in status: " + status);
        }
        this.status = MessageStatus.DELIVERED;
        this.deliveredAt = Instant.now();
        setUpdatedAt(Instant.now());
        incrementVersion();
    }

    /**
     * Marks the message as opened.
     */
    public void markOpened() {
        this.status = MessageStatus.OPENED;
        this.openedAt = Instant.now();
        setUpdatedAt(Instant.now());
        incrementVersion();
    }

    /**
     * Marks the message as failed.
     */
    public void markFailed(String reason) {
        this.status = MessageStatus.FAILED;
        this.failureReason = reason;
        this.active = false;
        setUpdatedAt(Instant.now());
        incrementVersion();
    }

    /**
     * Marks the message as bounced.
     */
    public void markBounced(String reason) {
        this.status = MessageStatus.BOUNCED;
        this.failureReason = reason;
        this.active = false;
        setUpdatedAt(Instant.now());
        incrementVersion();
    }

    /**
     * Adds an event to the message.
     */
    public void addEvent(MessageEvent event) {
        events.add(event);
        setUpdatedAt(Instant.now());
        incrementVersion();
    }

    /**
     * Gets the primary recipient.
     */
    public String getPrimaryRecipient() {
        return toAddresses.isEmpty() ? null : toAddresses.get(0);
    }

    /**
     * Gets the recipient count.
     */
    public int getRecipientCount() {
        return toAddresses.size() + ccAddresses.size() + bccAddresses.size();
    }

    // Getters
    public String getMessageId() { return messageId; }
    public MessageType getType() { return type; }
    public String getFromAddress() { return fromAddress; }
    public List<String> getToAddresses() { return Collections.unmodifiableList(toAddresses); }
    public List<String> getCcAddresses() { return Collections.unmodifiableList(ccAddresses); }
    public List<String> getBccAddresses() { return Collections.unmodifiableList(bccAddresses); }
    public String getSubject() { return subject; }
    public String getBody() { return body; }
    public String getHtmlBody() { return htmlBody; }
    public String getRawContent() { return rawContent; }
    public MessageStatus getStatus() { return status; }
    public String getTemplateId() { return templateId; }
    public Map<String, String> getTemplateData() { return templateData; }
    public List<String> getAttachments() { return Collections.unmodifiableList(attachments); }
    public String getPriority() { return priority; }
    public String getSender() { return sender; }
    public String getReplyTo() { return replyTo; }
    public boolean isTracked() { return tracked; }
    public boolean isOpenTracking() { return openTracking; }
    public boolean isClickTracking() { return clickTracking; }
    public Instant getSentAt() { return sentAt; }
    public Instant getDeliveredAt() { return deliveredAt; }
    public Instant getOpenedAt() { return openedAt; }
    public List<MessageEvent> getEvents() { return Collections.unmodifiableList(events); }
    public String getFailureReason() { return failureReason; }
    public String getNotes() { return notes; }
    public boolean isActive() { return active; }
    public String getTenantId() { return tenantId; }
    public String getUserId() { return userId; }

    public void setMessageId(String messageId) {
        this.messageId = messageId;
        setUpdatedAt(Instant.now());
        incrementVersion();
    }

    public void setPriority(String priority) {
        this.priority = priority;
        setUpdatedAt(Instant.now());
        incrementVersion();
    }

    public void setSender(String sender) {
        this.sender = sender;
        setUpdatedAt(Instant.now());
        incrementVersion();
    }

    public void setReplyTo(String replyTo) {
        this.replyTo = replyTo;
        setUpdatedAt(Instant.now());
        incrementVersion();
    }

    public void setTracked(boolean tracked) {
        this.tracked = tracked;
        setUpdatedAt(Instant.now());
        incrementVersion();
    }

    public void setOpenTracking(boolean openTracking) {
        this.openTracking = openTracking;
        setUpdatedAt(Instant.now());
        incrementVersion();
    }

    public void setClickTracking(boolean clickTracking) {
        this.clickTracking = clickTracking;
        setUpdatedAt(Instant.now());
        incrementVersion();
    }

    public void setNotes(String notes) {
        this.notes = notes;
        setUpdatedAt(Instant.now());
        incrementVersion();
    }

    public void setTenantId(String tenantId) {
        this.tenantId = tenantId;
        setUpdatedAt(Instant.now());
        incrementVersion();
    }

    public void setUserId(String userId) {
        this.userId = userId;
        setUpdatedAt(Instant.now());
        incrementVersion();
    }

    @Override
    public String toString() {
        return "Message{" +
                "id=" + getId() +
                ", type=" + type +
                ", subject='" + subject + '\'' +
                ", to=" + toAddresses +
                ", status=" + status +
                '}';
    }

    /**
     * Message event value object.
     */
    public static final class MessageEvent implements ValueObject {
        private static final long serialVersionUID = 1L;

        private final String type;
        private final String description;
        private final Instant timestamp;
        private final String ipAddress;
        private final String userAgent;
        private final String additionalData;

        public MessageEvent(String type, String description, String ipAddress, String userAgent, String additionalData) {
            this.type = type;
            this.description = description;
            this.ipAddress = ipAddress;
            this.userAgent = userAgent;
            this.additionalData = additionalData;
            this.timestamp = Instant.now();
            validate();
        }

        @Override
        public void validate() {
            if (type == null || type.trim().isEmpty()) {
                throw new IllegalArgumentException("Event type cannot be empty");
            }
        }

        public String getType() { return type; }
        public String getDescription() { return description; }
        public Instant getTimestamp() { return timestamp; }
        public String getIpAddress() { return ipAddress; }
        public String getUserAgent() { return userAgent; }
        public String getAdditionalData() { return additionalData; }

        @Override
        public String toString() {
            return "MessageEvent{" +
                    "type='" + type + '\'' +
                    ", timestamp=" + timestamp +
                    '}';
        }
    }
}
