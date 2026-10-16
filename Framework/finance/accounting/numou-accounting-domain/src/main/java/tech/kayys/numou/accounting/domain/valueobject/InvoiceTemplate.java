package tech.kayys.syirkah.accounting.domain.valueobject;

import tech.kayys.syirkah.foundation.domain.valueobject.ValueObject;

import java.util.Objects;

/**
 * Invoice template configuration.
 */
public final class InvoiceTemplate implements ValueObject {
    
    private static final long serialVersionUID = 1L;
    
    private final String id;
    private final String name;
    private final String description;
    private final String header;
    private final String footer;
    private final String logoUrl;
    private final String colorScheme;
    private final boolean showTaxBreakdown;
    private final boolean showDiscountBreakdown;
    private final String language;

    public InvoiceTemplate(
            String id,
            String name,
            String description,
            String header,
            String footer,
            String logoUrl,
            String colorScheme,
            boolean showTaxBreakdown,
            boolean showDiscountBreakdown,
            String language) {
        this.id = id;
        this.name = name;
        this.description = description;
        this.header = header;
        this.footer = footer;
        this.logoUrl = logoUrl;
        this.colorScheme = colorScheme;
        this.showTaxBreakdown = showTaxBreakdown;
        this.showDiscountBreakdown = showDiscountBreakdown;
        this.language = language;
        validate();
    }

    public void validate() {
        if (id == null || id.trim().isEmpty()) {
            throw new IllegalArgumentException("Template ID cannot be empty");
        }
        if (name == null || name.trim().isEmpty()) {
            throw new IllegalArgumentException("Template name cannot be empty");
        }
    }

    public String getId() { return id; }
    public String getName() { return name; }
    public String getDescription() { return description; }
    public String getHeader() { return header; }
    public String getFooter() { return footer; }
    public String getLogoUrl() { return logoUrl; }
    public String getColorScheme() { return colorScheme; }
    public boolean isShowTaxBreakdown() { return showTaxBreakdown; }
    public boolean isShowDiscountBreakdown() { return showDiscountBreakdown; }
    public String getLanguage() { return language; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        InvoiceTemplate that = (InvoiceTemplate) o;
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private String id;
        private String name;
        private String description;
        private String header;
        private String footer;
        private String logoUrl;
        private String colorScheme = "#1a73e8";
        private boolean showTaxBreakdown = true;
        private boolean showDiscountBreakdown = true;
        private String language = "en";

        public Builder id(String id) {
            this.id = id;
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

        public Builder header(String header) {
            this.header = header;
            return this;
        }

        public Builder footer(String footer) {
            this.footer = footer;
            return this;
        }

        public Builder logoUrl(String logoUrl) {
            this.logoUrl = logoUrl;
            return this;
        }

        public Builder colorScheme(String colorScheme) {
            this.colorScheme = colorScheme;
            return this;
        }

        public Builder showTaxBreakdown(boolean showTaxBreakdown) {
            this.showTaxBreakdown = showTaxBreakdown;
            return this;
        }

        public Builder showDiscountBreakdown(boolean showDiscountBreakdown) {
            this.showDiscountBreakdown = showDiscountBreakdown;
            return this;
        }

        public Builder language(String language) {
            this.language = language;
            return this;
        }

        public InvoiceTemplate build() {
            return new InvoiceTemplate(
                id, name, description, header, footer, logoUrl,
                colorScheme, showTaxBreakdown, showDiscountBreakdown, language
            );
        }
    }
}