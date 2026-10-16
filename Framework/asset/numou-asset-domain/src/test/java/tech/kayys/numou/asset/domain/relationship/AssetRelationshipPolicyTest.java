package tech.kayys.syirkah.asset.domain.relationship;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Domain unit tests for ASSET-17 relationship integrity rules. */
@DisplayName("Asset relationship integrity")
class AssetRelationshipPolicyTest {

    @Test
    @DisplayName("self-relation is rejected")
    void rejectsSelfRelation() {
        UUID id = UUID.randomUUID();
        assertThrows(IllegalArgumentException.class, () -> AssetRelationshipPolicy
                .validateNewRelationship(id, id, AssetRelationshipType.COMPONENT_OF));
    }

    @Test
    @DisplayName("null inputs are rejected")
    void rejectsNulls() {
        UUID id = UUID.randomUUID();
        assertThrows(NullPointerException.class, () -> AssetRelationshipPolicy
                .validateNewRelationship(null, id, AssetRelationshipType.COMPONENT_OF));
        assertThrows(NullPointerException.class, () -> AssetRelationshipPolicy
                .validateNewRelationship(id, null, AssetRelationshipType.COMPONENT_OF));
        assertThrows(NullPointerException.class, () -> AssetRelationshipPolicy
                .validateNewRelationship(id, UUID.randomUUID(), null));
    }

    @Test
    @DisplayName("hierarchical flags follow ASSET-17 §17.2")
    void hierarchicalFlags() {
        assertTrue(AssetRelationshipType.COMPONENT_OF.hierarchical());
        assertTrue(AssetRelationshipType.INSTALLED_ON.hierarchical());
        assertTrue(AssetRelationshipType.ATTACHED_TO.hierarchical());
        assertTrue(AssetRelationshipType.COMPONENT.hierarchical());
        assertTrue(AssetRelationshipType.PARENT.hierarchical());
        assertTrue(AssetRelationshipType.CHILD.hierarchical());
        assertFalse(AssetRelationshipType.SPARE.hierarchical());
        assertFalse(AssetRelationshipType.LINKED.hierarchical());
        assertFalse(AssetRelationshipType.RELATED_TO.hierarchical());
    }
}
