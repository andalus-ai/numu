package tech.kayys.syirkah.accounting.domain.audit;

import java.util.Objects;
import java.util.UUID;

public record WorkingPaperId(String value) {
    public WorkingPaperId {
        Objects.requireNonNull(value, "value must not be null");
        if (value.isBlank()) throw new IllegalArgumentException("WorkingPaperId must not be blank");
    }
    public static WorkingPaperId newId() {
        return new WorkingPaperId(UUID.randomUUID().toString());
    }
    public static WorkingPaperId of(String value) {
        return new WorkingPaperId(value);
    }
}
