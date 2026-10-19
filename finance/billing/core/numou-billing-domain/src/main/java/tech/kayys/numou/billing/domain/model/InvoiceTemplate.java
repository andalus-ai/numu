package tech.kayys.numou.billing.domain.model;

import java.util.UUID;
import tech.kayys.numou.foundation.domain.valueobject.ValueObject;

import java.util.HashMap;
import java.util.Map;

/**
 * Invoice template value object.
 * Defines the layout and style of generated invoices.
 */
public final class InvoiceTemplate implements ValueObject {
    
    private static final long serialVersionUID = 1L;
    
    private final String templateId;
    private final String name;
    private final String description;
    private final String language;
    private final String currencyCode;
    private final String headerHtml;
    private final String footerHtml;
    private final String stylesCss;
    private final Map<String, String> placeholders;
    private final boolean isDefault;
    private final boolean active;

    public InvoiceTemplate(
            String templateId,
            String name,
            String description,
            String language,
            String currencyCode,
            String headerHtml,
            String footerHtml,
            String stylesCss,
            Map<String, String> placeholders,
            boolean isDefault,
            boolean active) {
        this.templateId = templateId;
        this.name = name;
        this.description = description;
        this.language = language;
        this.currencyCode = currencyCode;
        this.headerHtml = headerHtml;
        this.footerHtml = footerHtml;
        this.stylesCss = stylesCss;
        this.placeholders = placeholders != null ? new HashMap<>(placeholders) : new HashMap<>();
        this.isDefault = isDefault;
        this.active = active;
        validate();
    }

    @Override
    public void validate() {
        if (templateId == null || templateId.trim().isEmpty()) {
            throw new IllegalArgumentException("Template ID cannot be empty");
        }
        if (name == null || name.trim().isEmpty()) {
            throw new IllegalArgumentException("Template name cannot be empty");
        }
    }

    // Getters
    public String getTemplateId() { return templateId; }
    public String getName() { return name; }
    public String getDescription() { return description; }
    public String getLanguage() { return language; }
    public String getCurrencyCode() { return currencyCode; }
    public String getHeaderHtml() { return headerHtml; }
    public String getFooterHtml() { return footerHtml; }
    public String getStylesCss() { return stylesCss; }
    public Map<String, String> getPlaceholders() { return placeholders; }
    public boolean isDefault() { return isDefault; }
    public boolean isActive() { return active; }

    public String render(String data) {
        // Template rendering logic
        // In production, use a proper template engine like Thymeleaf, Freemarker, or Velocity
        String rendered = headerHtml + data + footerHtml;
        for (Map.Entry<String, String> entry : placeholders.entrySet()) {
            rendered = rendered.replace("{{" + entry.getKey() + "}}", entry.getValue());
        }
        return rendered;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private String templateId;
        private String name;
        private String description;
        private String language = "en";
        private String currencyCode = "USD";
        private String headerHtml;
        private String footerHtml;
        private String stylesCss;
        private Map<String, String> placeholders = new HashMap<>();
        private boolean isDefault = false;
        private boolean active = true;

        public Builder templateId(String templateId) {
            this.templateId = templateId;
            return this;
        }

        public Builder name(String name) {
            this.name = name;
            return this;
        }

        public Builder description(String description) {
            this.description = description;
            return this;
        }

        public Builder language(String language) {
            this.language = language;
            return this;
        }

        public Builder currencyCode(String currencyCode) {
            this.currencyCode = currencyCode;
            return this;
        }

        public Builder headerHtml(String headerHtml) {
            this.headerHtml = headerHtml;
            return this;
        }

        public Builder footerHtml(String footerHtml) {
            this.footerHtml = footerHtml;
            return this;
        }

        public Builder stylesCss(String stylesCss) {
            this.stylesCss = stylesCss;
            return this;
        }

        public Builder placeholder(String key, String value) {
            this.placeholders.put(key, value);
            return this;
        }

        public Builder isDefault(boolean isDefault) {
            this.isDefault = isDefault;
            return this;
        }

        public Builder active(boolean active) {
            this.active = active;
            return this;
        }

        public InvoiceTemplate build() {
            if (templateId == null) {
                templateId = UUID.randomUUID().toString();
            }
            return new InvoiceTemplate(
                templateId, name, description, language, currencyCode,
                headerHtml, footerHtml, stylesCss, placeholders,
                isDefault, active
            );
        }
    }
}