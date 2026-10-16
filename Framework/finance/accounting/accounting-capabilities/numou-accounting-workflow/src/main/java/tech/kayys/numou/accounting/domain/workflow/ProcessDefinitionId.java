package tech.kayys.syirkah.accounting.domain.workflow;

import java.util.Objects;

/** Identity of a {@link ProcessDefinition} including code and version. */
public record ProcessDefinitionId(String code, int version) {
    public ProcessDefinitionId {
        Objects.requireNonNull(code, "code");
        if (code.isBlank()) throw new IllegalArgumentException("code must not be blank");
        if (version < 1) throw new IllegalArgumentException("version must be >= 1");
    }
    public static ProcessDefinitionId of(String code, int version) { return new ProcessDefinitionId(code, version); }
}
