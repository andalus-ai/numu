package tech.kayys.numou.planning.application;

import tech.kayys.numou.planning.application.port.ScopeQuantityRepository;
import tech.kayys.numou.planning.domain.ScopeQuantityDefinition;

import java.util.Objects;

/** Creates append-only quantity definition revisions within an explicit tenant boundary. */
public final class CreateScopeQuantityDefinition {
    private final ScopeQuantityRepository repository;

    public CreateScopeQuantityDefinition(ScopeQuantityRepository repository) {
        this.repository = Objects.requireNonNull(repository, "Scope quantity repository cannot be null");
    }

    public ScopeQuantityDefinition execute(ScopeQuantityDefinition definition) {
        Objects.requireNonNull(definition, "Definition cannot be null");
        var previous = repository.findDefinitionsForScope(
                definition.tenantId(), definition.scopeType(), definition.scopeId()).stream()
                .filter(d -> d.code().equals(definition.code()))
                .mapToInt(ScopeQuantityDefinition::revision)
                .max().orElse(0);
        if (definition.revision() != previous + 1) {
            throw new IllegalArgumentException("Quantity revision must be the next sequential revision: expected "
                    + (previous + 1));
        }
        if (!repository.createDefinition(definition)) {
            throw new IllegalArgumentException("Quantity definition ID already exists");
        }
        return definition;
    }
}
