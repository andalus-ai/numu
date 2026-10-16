package tech.kayys.syirkah.construction.domain.masterdata;

import tech.kayys.syirkah.foundation.domain.entity.AbstractAggregateRoot;
import java.util.Objects;
import java.util.UUID;

public final class ConstructionSpecification extends AbstractAggregateRoot<ConstructionSpecificationId> {
    private final String specCode;
    private final String title;
    private final SpecificationType type;
    private final String content;

    public ConstructionSpecification(ConstructionSpecificationId id, String specCode, String title, SpecificationType type, String content) {
        super(id);
        this.specCode = Objects.requireNonNull(specCode);
        this.title = Objects.requireNonNull(title);
        this.type = Objects.requireNonNull(type);
        this.content = Objects.requireNonNull(content);
    }

    public static ConstructionSpecification create(String specCode, String title, SpecificationType type, String content) {
        return new ConstructionSpecification(ConstructionSpecificationId.generate(), specCode, title, type, content);
    }

    public String specCode() { return specCode; }
    public String title() { return title; }
    public SpecificationType type() { return type; }
    public String content() { return content; }
}
