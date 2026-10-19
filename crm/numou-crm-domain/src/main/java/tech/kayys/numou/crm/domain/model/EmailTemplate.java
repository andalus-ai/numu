package tech.kayys.numou.crm.domain.model;

import tech.kayys.numou.foundation.domain.entity.AbstractAggregateRoot;
import tech.kayys.numou.crm.domain.identifier.EmailTemplateId;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.HashMap;

/**
 * Email template aggregate root.
 * Defines reusable email templates for CRM communications.
 */
public final class EmailTemplate extends AbstractAggregateRoot<EmailTemplateId> {
    
    private static final long serialVersionUID = 1L;
    
    private String name;
    private String description;
    private String subject;
    private String body;
    private String htmlBody;
    private String category;
    private List<String> tags;
    private Map<String, String> defaultVariables;
    private String fromEmail;
    private String fromName;
    private String replyTo;
    private boolean active;
    private String createdBy;
    private String notes;

    private EmailTemplate(EmailTemplateId id) {
        super(id);
        this.tags = new ArrayList<>();
        this.active = true;
    }

    private EmailTemplate() {
        super();
    }

    /**
     * Factory method to create a new email template.
     */
    public static EmailTemplate create(
            EmailTemplateId id,
            String name,
            String subject,
            String body,
            String category,
            String createdBy) {
        EmailTemplate template = new EmailTemplate(id);
        template.name = name;
        template.subject = subject;
        template.body = body;
        template.category = category;
        template.createdBy = createdBy;
        return template;
    }

    /**
     * Updates the template content.
     */
    public void updateContent(String subject, String body, String htmlBody) {
        this.subject = subject;
        this.body = body;
        this.htmlBody = htmlBody;
        setUpdatedAt(Instant.now());
        incrementVersion();
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
     * Adds a tag to the template.
     */
    public void addTag(String tag) {
        if (!tags.contains(tag)) {
            tags.add(tag);
            setUpdatedAt(Instant.now());
            incrementVersion();
        }
    }

    /**
     * Sets default variables.
     */
    public void setDefaultVariables(Map<String, String> defaultVariables) {
        this.defaultVariables = defaultVariables;
        setUpdatedAt(Instant.now());
        incrementVersion();
    }

    /**
     * Renders the template with variables.
     */
    public String render(Map<String, String> variables, boolean html) {
        String content = html ? htmlBody : body;
        if (content == null) {
            return "";
        }
        
        // Merge default variables
        Map<String, String> mergedVariables = defaultVariables != null ? 
            new HashMap<>(defaultVariables) : new HashMap<>();
        if (variables != null) {
            mergedVariables.putAll(variables);
        }
        
        String rendered = content;
        for (Map.Entry<String, String> entry : mergedVariables.entrySet()) {
            rendered = rendered.replace("{{" + entry.getKey() + "}}", entry.getValue());
        }
        return rendered;
    }

    /**
     * Activates the template.
     */
    public void activate() {
        this.active = true;
        setUpdatedAt(Instant.now());
        incrementVersion();
    }

    /**
     * Deactivates the template.
     */
    public void deactivate() {
        this.active = false;
        setUpdatedAt(Instant.now());
        incrementVersion();
    }

    // Getters
    public String getName() { return name; }
    public String getDescription() { return description; }
    public String getSubject() { return subject; }
    public String getBody() { return body; }
    public String getHtmlBody() { return htmlBody; }
    public String getCategory() { return category; }
    public List<String> getTags() { return Collections.unmodifiableList(tags); }
    public Map<String, String> getDefaultVariables() { return defaultVariables != null ? 
        Collections.unmodifiableMap(defaultVariables) : null; }
    public String getFromEmail() { return fromEmail; }
    public String getFromName() { return fromName; }
    public String getReplyTo() { return replyTo; }
    public boolean isActive() { return active; }
    public String getCreatedBy() { return createdBy; }
    public String getNotes() { return notes; }

    public void setDescription(String description) {
        this.description = description;
        setUpdatedAt(Instant.now());
        incrementVersion();
    }

    public void setFromEmail(String fromEmail) {
        this.fromEmail = fromEmail;
        setUpdatedAt(Instant.now());
        incrementVersion();
    }

    public void setFromName(String fromName) {
        this.fromName = fromName;
        setUpdatedAt(Instant.now());
        incrementVersion();
    }

    public void setReplyTo(String replyTo) {
        this.replyTo = replyTo;
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
        return "EmailTemplate{" +
                "id=" + getId() +
                ", name='" + name + '\'' +
                ", category='" + category + '\'' +
                ", active=" + active +
                '}';
    }
}