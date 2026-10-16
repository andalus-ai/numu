package tech.kayys.syirkah.product.domain.classification;

import tech.kayys.syirkah.foundation.domain.entity.AbstractAggregateRoot;
import tech.kayys.syirkah.foundation.domain.exception.BusinessRuleViolation;
import tech.kayys.syirkah.foundation.domain.exception.InvalidStateException;
import tech.kayys.syirkah.product.domain.event.ClassificationNodeArchived;
import tech.kayys.syirkah.product.domain.event.ClassificationNodeCreated;
import tech.kayys.syirkah.product.domain.event.ClassificationNodeMoved;
import tech.kayys.syirkah.product.domain.event.ClassificationNodeRenamed;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * A single entry in a classification scheme's taxonomy. Nodes form a
 * tree through {@code parentNodeId} (null = root) but each node is its
 * own aggregate so large taxonomies stay cheap to load and update.
 *
 * {@link #moveTo(ClassificationNodeId)} only rejects making a node its
 * own parent; full cycle detection needs the whole ancestor chain and
 * therefore lives in the application layer, which can query the node
 * repository (product02.md, section 5).
 */
public final class ClassificationNode
        extends AbstractAggregateRoot<ClassificationNodeId> {

    private final ClassificationSchemeId schemeId;

    private ClassificationNodeId parentNodeId;

    private final String code;

    private String name;

    private ClassificationNodeStatus status;

    private int sortOrder;

    private ClassificationNode(
            ClassificationNodeId id,
            ClassificationSchemeId schemeId,
            ClassificationNodeId parentNodeId,
            String code,
            String name,
            int sortOrder
    ) {
        super(id);

        this.schemeId = Objects.requireNonNull(
                schemeId,
                "Classification node schemeId cannot be null"
        );
        this.parentNodeId = parentNodeId;
        this.code = requireText(code, "Classification node code");
        this.name = requireText(name, "Classification node name");
        this.sortOrder = sortOrder;
        this.status = ClassificationNodeStatus.ACTIVE;
    }

    public static ClassificationNode create(
            ClassificationNodeId id,
            ClassificationSchemeId schemeId,
            ClassificationNodeId parentNodeId,
            String code,
            String name,
            int sortOrder
    ) {
        ClassificationNode node = new ClassificationNode(
                id,
                schemeId,
                parentNodeId,
                code,
                name,
                sortOrder
        );

        node.raise(
                new ClassificationNodeCreated(
                        UUID.randomUUID(),
                        Instant.now(),
                        id,
                        schemeId,
                        parentNodeId,
                        node.code,
                        node.name
                )
        );

        return node;
    }

    public void rename(String newName) {
        ensureActive();

        String normalized = requireText(newName, "Classification node name");

        if (normalized.equals(name)) {
            return;
        }

        String oldName = name;
        name = normalized;

        raise(
                new ClassificationNodeRenamed(
                        UUID.randomUUID(),
                        Instant.now(),
                        id(),
                        oldName,
                        normalized
                )
        );
    }

    public void moveTo(ClassificationNodeId newParentNodeId) {
        ensureActive();

        if (id().equals(newParentNodeId)) {
            throw new BusinessRuleViolation(
                    "Classification node cannot be its own parent"
            );
        }

        if (Objects.equals(parentNodeId, newParentNodeId)) {
            return;
        }

        ClassificationNodeId oldParentNodeId = parentNodeId;
        parentNodeId = newParentNodeId;

        raise(
                new ClassificationNodeMoved(
                        UUID.randomUUID(),
                        Instant.now(),
                        id(),
                        oldParentNodeId,
                        newParentNodeId
                )
        );
    }

    public void changeSortOrder(int newSortOrder) {
        ensureActive();

        this.sortOrder = newSortOrder;
    }

    public void archive() {
        if (status != ClassificationNodeStatus.ACTIVE) {
            throw new InvalidStateException(
                    "Only active classification nodes can be archived"
            );
        }

        status = ClassificationNodeStatus.ARCHIVED;

        raise(
                new ClassificationNodeArchived(
                        UUID.randomUUID(),
                        Instant.now(),
                        id()
                )
        );
    }

    private void ensureActive() {
        if (status != ClassificationNodeStatus.ACTIVE) {
            throw new InvalidStateException(
                    "Archived classification node cannot be modified"
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

    public ClassificationSchemeId schemeId() {
        return schemeId;
    }

    public ClassificationNodeId parentNodeId() {
        return parentNodeId;
    }

    public String code() {
        return code;
    }

    public String name() {
        return name;
    }

    public ClassificationNodeStatus status() {
        return status;
    }

    public int sortOrder() {
        return sortOrder;
    }
}
