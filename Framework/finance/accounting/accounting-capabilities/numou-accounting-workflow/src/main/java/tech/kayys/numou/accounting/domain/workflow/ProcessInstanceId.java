package tech.kayys.syirkah.accounting.domain.workflow;

import java.util.Objects;
import java.util.UUID;

/** Identity of a running {@link ProcessInstance}. */
public record ProcessInstanceId(String value) {
    public ProcessInstanceId {
        Objects.requireNonNull(value, "value");
        if (value.isBlank()) throw new IllegalArgumentException("ProcessInstanceId must not be blank");
    }
    public static ProcessInstanceId generate() { return new ProcessInstanceId(UUID.randomUUID().toString()); }
    public static ProcessInstanceId of(String v) { return new ProcessInstanceId(v); }
}
