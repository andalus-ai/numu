package tech.kayys.syirkah.accounting.domain.workflow;

import java.util.Objects;
import java.util.UUID;

/** Identity of a human task. */
public record TaskId(String value) {
    public TaskId {
        Objects.requireNonNull(value, "value");
        if (value.isBlank()) throw new IllegalArgumentException("TaskId must not be blank");
    }
    public static TaskId generate() { return new TaskId(UUID.randomUUID().toString()); }
    public static TaskId of(String v) { return new TaskId(v); }
}
