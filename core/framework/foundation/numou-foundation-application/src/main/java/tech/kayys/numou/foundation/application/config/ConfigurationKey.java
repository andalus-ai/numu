package tech.kayys.numou.foundation.application.config;

import java.util.Objects;
import java.util.Optional;

/**
 * Strongly typed configuration key (config01.md §P4-02 #6).
 *
 * @param <T> the type of the configuration property
 */
public final class ConfigurationKey<T> {

    private final String name;
    private final Class<T> type;
    private final boolean required;
    private final T defaultValue;
    private final boolean sensitive;

    private ConfigurationKey(
            String name,
            Class<T> type,
            boolean required,
            T defaultValue,
            boolean sensitive) {

        this.name = Objects.requireNonNull(name, "key name cannot be null");
        this.type = Objects.requireNonNull(type, "key type cannot be null");
        this.required = required;
        this.defaultValue = defaultValue;
        this.sensitive = sensitive;
    }

    public static <T> ConfigurationKey<T> required(String name, Class<T> type) {
        return new ConfigurationKey<>(name, type, true, null, false);
    }

    public static <T> ConfigurationKey<T> optional(String name, Class<T> type) {
        return new ConfigurationKey<>(name, type, false, null, false);
    }

    public static <T> ConfigurationKey<T> optional(String name, Class<T> type, T defaultValue) {
        return new ConfigurationKey<>(name, type, false, defaultValue, false);
    }

    public static <T> ConfigurationKey<T> sensitive(String name, Class<T> type) {
        return new ConfigurationKey<>(name, type, false, null, true);
    }

    public static <T> ConfigurationKey<T> sensitiveRequired(String name, Class<T> type) {
        return new ConfigurationKey<>(name, type, true, null, true);
    }

    public static <T> Builder<T> builder(String name, Class<T> type) {
        return new Builder<>(name, type);
    }

    public static final class Builder<T> {
        private final String name;
        private final Class<T> type;
        private boolean required;
        private T defaultValue;
        private boolean sensitive;
        private String description;

        public Builder(String name, Class<T> type) {
            this.name = Objects.requireNonNull(name, "key name cannot be null");
            this.type = Objects.requireNonNull(type, "key type cannot be null");
        }

        public Builder<T> required() {
            this.required = true;
            return this;
        }

        public Builder<T> defaultValue(T defaultValue) {
            this.defaultValue = defaultValue;
            return this;
        }

        public Builder<T> sensitive() {
            this.sensitive = true;
            return this;
        }

        public Builder<T> description(String description) {
            this.description = description;
            return this;
        }

        public ConfigurationKey<T> build() {
            return new ConfigurationKey<>(name, type, required, defaultValue, sensitive);
        }
    }

    public String name() {
        return name;
    }

    public Class<T> type() {
        return type;
    }

    public boolean required() {
        return required;
    }

    public Optional<T> defaultValue() {
        return Optional.ofNullable(defaultValue);
    }

    public boolean sensitive() {
        return sensitive;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ConfigurationKey<?> that)) return false;
        return name.equals(that.name);
    }

    @Override
    public int hashCode() {
        return name.hashCode();
    }

    @Override
    public String toString() {
        return "ConfigurationKey[" + name + " (" + type.getSimpleName() + ")]";
    }
}
