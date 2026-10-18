package tech.kayys.numou.scope.domain;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class BreakdownNodeTest {
    @Test
    void createsRootAndChildNodesWithoutProjectOrConstructionTypes() {
        var structureId = BreakdownStructureId.generate();
        var root = BreakdownNode.create(structureId, null, "1", "Deliverables", "work_package", 0);
        var child = BreakdownNode.create(structureId, root.id(), "1.1", "Design", "activity", 1);

        assertTrue(root.isRoot());
        assertTrue(root.parent().isEmpty());
        assertFalse(child.isRoot());
        assertEquals(root.id(), child.parent().orElseThrow());
        assertEquals("ACTIVITY", child.kind());
        assertEquals(structureId, child.structureId());
    }

    @Test
    void rejectsSelfParentingBlankFieldsAndNegativePosition() {
        var structureId = BreakdownStructureId.generate();
        var nodeId = BreakdownNodeId.generate();

        assertThrows(IllegalArgumentException.class, () -> new BreakdownNode(
                nodeId, structureId, nodeId, "1", "Root", "GROUP", 0));
        assertThrows(IllegalArgumentException.class, () -> BreakdownNode.create(
                structureId, null, " ", "Name", "GROUP", 0));
        assertThrows(IllegalArgumentException.class, () -> BreakdownNode.create(
                structureId, null, "1", "Name", "GROUP", -1));
    }
}
