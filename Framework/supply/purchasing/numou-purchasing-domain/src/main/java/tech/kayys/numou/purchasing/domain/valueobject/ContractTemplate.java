package tech.kayys.syirkah.purchasing.domain.valueobject;

import tech.kayys.syirkah.foundation.domain.valueobject.ValueObject;

import java.util.Objects;

/**
 * Contract template value object.
 */
public final class ContractTemplate implements ValueObject {

    private static final long serialVersionUID = 1L;

    private final String id;
    private final String name;
    private final String description;
    private final ContractType contractType;
    private final String content;
    private final String language;
    private final String version;
    private final boolean active;

    public ContractTemplate(
            String id,
            String name,
            String description,
            ContractType contractType,
            String content,
            String language,
            String version,
            boolean active) {
        this.id = id;
        this.name = name;
        this.description = description;
        this.contractType = contractType;
        this.content = content;
        this.language = language;
        this.version = version;
        this.active = active;
        validate();
    }

    @Override
    public void validate() {
        if (id == null || id.trim().isEmpty()) {
            throw new IllegalArgumentException("Template ID cannot be empty");
        }
        if (name == null || name.trim().isEmpty()) {
            throw new IllegalArgumentException("Template name cannot be empty");
        }
        if (contractType == null) {
            throw new IllegalArgumentException("Contract type is required");
        }
        if (content == null || content.trim().isEmpty()) {
            throw new IllegalArgumentException("Content cannot be empty");
        }
    }

    public String getId() { return id; }
    public String getName() { return name; }
    public String getDescription() { return description; }
    public ContractType getContractType() { return contractType; }
    public String getContent() { return content; }
    public String getLanguage() { return language; }
    public String getVersion() { return version; }
    public boolean isActive() { return active; }

    public String renderWithData(String data) {
        // Simple template rendering - in production, use a proper template engine
        return content.replace("{{data}}", data);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ContractTemplate that = (ContractTemplate) o;
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    @Override
    public String toString() {
        return "ContractTemplate{" +
                "id='" + id + '\'' +
                ", name='" + name + '\'' +
                ", contractType=" + contractType +
                ", version='" + version + '\'' +
                '}';
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private String id;
        private String name;
        private String description;
        private ContractType contractType;
        private String content;
        private String language = "en";
        private String version = "1.0";
        private boolean active = true;

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

        public Builder contractType(ContractType contractType) {
            this.contractType = contractType;
            return this;
        }

        public Builder content(String content) {
            this.content = content;
            return this;
        }

        public Builder language(String language) {
            this.language = language;
            return this;
        }

        public Builder version(String version) {
            this.version = version;
            return this;
        }

        public Builder active(boolean active) {
            this.active = active;
            return this;
        }

        public ContractTemplate build() {
            return new ContractTemplate(id, name, description, contractType, content, language, version, active);
        }
    }
}
