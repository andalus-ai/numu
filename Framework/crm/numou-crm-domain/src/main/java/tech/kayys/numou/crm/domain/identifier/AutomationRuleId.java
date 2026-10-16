package tech.kayys.numou.crm.domain.identifier;

import tech.kayys.numou.foundation.domain.identifier.DomainId;

import java.util.Objects;
import java.io.Serializable;
import java.util.UUID;

public record AutomationRuleId(UUID value) implements DomainId<UUID>, Serializable {

    public AutomationRuleId {
        Objects.requireNonNull(value, "AutomationRuleId value cannot be null");
    }

    public static AutomationRuleId of(UUID value) {
        return new AutomationRuleId(value);
    }

    public static AutomationRuleId generate() {
        return new AutomationRuleId(UUID.randomUUID());
    }

    public static AutomationRuleId fromString(String value) {
        return new AutomationRuleId(UUID.fromString(value));
    }

    @Override
    public String toString() {
        return "AutomationRuleId{" + value + "}";
    }
}
