package tech.kayys.syirkah.product.domain.classification;

import tech.kayys.syirkah.foundation.domain.entity.AbstractAggregateRoot;
import tech.kayys.syirkah.foundation.domain.exception.InvalidStateException;
import tech.kayys.syirkah.product.domain.event.ClassificationSchemeActivated;
import tech.kayys.syirkah.product.domain.event.ClassificationSchemeArchived;
import tech.kayys.syirkah.product.domain.event.ClassificationSchemeCreated;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * A classification scheme defines <em>which taxonomy</em> we are
 * talking about (RETAIL, ACCOUNTING, TAX, ...). It deliberately does
 * not own its nodes: a taxonomy can hold hundreds of thousands of
 * entries, so each {@link ClassificationNode} is its own aggregate
 * referencing the scheme by id (product02.md).
 */
public final class ClassificationScheme
        extends AbstractAggregateRoot<ClassificationSchemeId> {

    private final String code;

    private String name;

    private final ClassificationSchemeType type;

    private ClassificationSchemeStatus status;

    private ClassificationScheme(
            ClassificationSchemeId id,
            String code,
            String name,
            ClassificationSchemeType type
    ) {
        super(id);

        this.code = requireText(code, "Classification scheme code");
        this.name = requireText(name, "Classification scheme name");
        this.type = Objects.requireNonNull(
                type,
                "Classification scheme type cannot be null"
        );
        this.status = ClassificationSchemeStatus.DRAFT;
    }

    public static ClassificationScheme create(
            ClassificationSchemeId id,
            String code,
            String name,
            ClassificationSchemeType type
    ) {
        ClassificationScheme scheme =
                new ClassificationScheme(id, code, name, type);

        scheme.raise(
                new ClassificationSchemeCreated(
                        UUID.randomUUID(),
                        Instant.now(),
                        id,
                        scheme.code,
                        scheme.name,
                        type
                )
        );

        return scheme;
    }

    public void activate() {
        requireStatus(ClassificationSchemeStatus.DRAFT);

        status = ClassificationSchemeStatus.ACTIVE;

        raise(
                new ClassificationSchemeActivated(
                        UUID.randomUUID(),
                        Instant.now(),
                        id()
                )
        );
    }

    public void archive() {
        requireStatus(ClassificationSchemeStatus.ACTIVE);

        status = ClassificationSchemeStatus.ARCHIVED;

        raise(
                new ClassificationSchemeArchived(
                        UUID.randomUUID(),
                        Instant.now(),
                        id()
                )
        );
    }

    public void rename(String newName) {
        if (status == ClassificationSchemeStatus.ARCHIVED) {
            throw new InvalidStateException(
                    "Archived classification scheme cannot be renamed"
            );
        }

        this.name = requireText(newName, "Classification scheme name");
    }

    private void requireStatus(ClassificationSchemeStatus expected) {
        if (status != expected) {
            throw new InvalidStateException(
                    "Classification scheme must be in " + expected
                            + " state but was " + status
            );
        }
    }

    private static String requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(
                    field + " cannot be blank"
            );
        }

        return value.trim();
    }

    public String code() {
        return code;
    }

    public String name() {
        return name;
    }

    public ClassificationSchemeType type() {
        return type;
    }

    public ClassificationSchemeStatus status() {
        return status;
    }
}
