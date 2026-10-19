package tech.kayys.numou.scope.domain;

import tech.kayys.numou.foundation.domain.entity.AbstractAggregateRoot;

import java.util.Objects;

/**
 * Generic owner of breakdown metadata. Nodes are stored independently so
 * consumers need not load an entire tree to update one node.
 *
 * Owner references are opaque business identifiers: this module does not
 * depend on Project, Construction, Scheduling, or any persistence model.
 */
public final class BreakdownStructure extends AbstractAggregateRoot<BreakdownStructureId> {
    private final BreakdownStructureType type;
    private final String code;
    private String name;
    private final String ownerReference;
    private BreakdownStructureStatus status;

    private BreakdownStructure(
            BreakdownStructureId id,
            BreakdownStructureType type,
            String code,
            String name,
            String ownerReference) {
        super(Objects.requireNonNull(id, "Breakdown structure id cannot be null"));
        this.type = Objects.requireNonNull(type, "Breakdown structure type cannot be null");
        this.code = requireText(code, "Breakdown structure code");
        this.name = requireText(name, "Breakdown structure name");
        this.ownerReference = normalizeOptionalReference(ownerReference);
        this.status = BreakdownStructureStatus.DRAFT;
    }

    public static BreakdownStructure create(
            BreakdownStructureType type,
            String code,
            String name,
            String ownerReference) {
        return new BreakdownStructure(BreakdownStructureId.generate(), type, code, name, ownerReference);
    }

    public void rename(String name) {
        ensureDraft();
        this.name = requireText(name, "Breakdown structure name");
    }

    public void activate() {
        if (status != BreakdownStructureStatus.DRAFT) {
            throw new IllegalStateException("Only a draft breakdown structure can be activated");
        }
        status = BreakdownStructureStatus.ACTIVE;
    }

    public void retire() {
        if (status != BreakdownStructureStatus.ACTIVE) {
            throw new IllegalStateException("Only an active breakdown structure can be retired");
        }
        status = BreakdownStructureStatus.RETIRED;
    }

    private void ensureDraft() {
        if (status != BreakdownStructureStatus.DRAFT) {
            throw new IllegalStateException("Only a draft breakdown structure can be changed");
        }
    }

    private static String requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " cannot be blank");
        }
        return value.trim();
    }

    private static String normalizeOptionalReference(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    public BreakdownStructureType type() { return type; }
    public String code() { return code; }
    public String name() { return name; }
    public String ownerReference() { return ownerReference; }
    public BreakdownStructureStatus status() { return status; }
}
