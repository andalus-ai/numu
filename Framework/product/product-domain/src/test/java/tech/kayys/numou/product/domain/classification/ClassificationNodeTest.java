package tech.kayys.syirkah.product.domain.classification;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tech.kayys.syirkah.foundation.domain.exception.BusinessRuleViolation;
import tech.kayys.syirkah.foundation.domain.exception.InvalidStateException;
import tech.kayys.syirkah.product.domain.event.ClassificationNodeArchived;
import tech.kayys.syirkah.product.domain.event.ClassificationNodeCreated;
import tech.kayys.syirkah.product.domain.event.ClassificationNodeMoved;
import tech.kayys.syirkah.product.domain.event.ClassificationNodeRenamed;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("ClassificationNode aggregate")
class ClassificationNodeTest {

    private static final ClassificationSchemeId SCHEME =
            ClassificationSchemeId.generate();

    private static ClassificationNode newNode(
            ClassificationNodeId id,
            ClassificationNodeId parent
    ) {
        return ClassificationNode.create(
                id,
                SCHEME,
                parent,
                "COFFEE",
                "Coffee",
                0
        );
    }

    @Test
    void createsActiveNodeAndRaisesEvent() {
        var node = newNode(ClassificationNodeId.generate(), null);

        assertEquals(ClassificationNodeStatus.ACTIVE, node.status());
        assertNull(node.parentNodeId());

        var events = node.pullDomainEvents();
        assertEquals(1, events.size());
        assertInstanceOf(ClassificationNodeCreated.class, events.getFirst());
    }

    @Test
    void renamesNodeAndRaisesEvent() {
        var node = newNode(ClassificationNodeId.generate(), null);
        node.pullDomainEvents();

        node.rename("Fresh Coffee");

        assertEquals("Fresh Coffee", node.name());
        var renamed = assertInstanceOf(
                ClassificationNodeRenamed.class,
                node.pullDomainEvents().getFirst()
        );
        assertEquals("Coffee", renamed.oldName());
        assertEquals("Fresh Coffee", renamed.newName());
    }

    @Test
    void renamingToSameNameRaisesNoEvent() {
        var node = newNode(ClassificationNodeId.generate(), null);
        node.pullDomainEvents();

        node.rename("Coffee");

        assertTrue(node.pullDomainEvents().isEmpty());
    }

    @Test
    void movesNodeAndRaisesEvent() {
        var parent = ClassificationNodeId.generate();
        var node = newNode(ClassificationNodeId.generate(), null);
        node.pullDomainEvents();

        node.moveTo(parent);

        assertEquals(parent, node.parentNodeId());
        assertInstanceOf(
                ClassificationNodeMoved.class,
                node.pullDomainEvents().getFirst()
        );
    }

    @Test
    void rejectsBecomingOwnParent() {
        var id = ClassificationNodeId.generate();
        var node = newNode(id, null);

        assertThrows(BusinessRuleViolation.class, () -> node.moveTo(id));
    }

    @Test
    void changesSortOrderWhenActive() {
        var node = newNode(ClassificationNodeId.generate(), null);

        node.changeSortOrder(5);

        assertEquals(5, node.sortOrder());
    }

    @Test
    void archivesActiveNodeAndRaisesEvent() {
        var node = newNode(ClassificationNodeId.generate(), null);
        node.pullDomainEvents();

        node.archive();

        assertEquals(ClassificationNodeStatus.ARCHIVED, node.status());
        assertInstanceOf(
                ClassificationNodeArchived.class,
                node.pullDomainEvents().getFirst()
        );
    }

    @Test
    void rejectsModifyingArchivedNode() {
        var node = newNode(ClassificationNodeId.generate(), null);
        node.archive();

        assertThrows(
                InvalidStateException.class,
                () -> node.rename("Nope")
        );
        assertThrows(
                InvalidStateException.class,
                () -> node.moveTo(ClassificationNodeId.generate())
        );
        assertThrows(InvalidStateException.class, node::archive);
    }
}
