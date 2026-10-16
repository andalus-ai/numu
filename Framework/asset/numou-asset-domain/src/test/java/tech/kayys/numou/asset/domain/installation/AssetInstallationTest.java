package tech.kayys.syirkah.asset.domain.installation;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tech.kayys.syirkah.asset.domain.relationship.AssetRelationshipType;

import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

@DisplayName("AssetInstallation")
class AssetInstallationTest {

    private static final Instant NOW = Instant.parse("2026-01-10T00:00:00Z");

    @Test
    @DisplayName("rejects self installation")
    void rejectsSelf() {
        UUID id = UUID.randomUUID();
        assertThrows(IllegalArgumentException.class, () -> AssetInstallation.install(
                "t1", id, id, AssetRelationshipType.COMPONENT, NOW, "TECH-1"));
    }

    @Test
    @DisplayName("rejects LINKED relationship")
    void rejectsLinked() {
        assertThrows(IllegalArgumentException.class, () -> AssetInstallation.install(
                "t1", UUID.randomUUID(), UUID.randomUUID(), AssetRelationshipType.LINKED, NOW, "TECH-1"));
    }

    @Test
    @DisplayName("markRemoved transitions INSTALLED to REMOVED once")
    void removalTransition() {
        AssetInstallation installation = AssetInstallation.install(
                "t1", UUID.randomUUID(), UUID.randomUUID(), AssetRelationshipType.COMPONENT, NOW, "TECH-1");
        AssetInstallation removed = installation.markRemoved(NOW.plusSeconds(10), "TECH-42");
        assertEquals(AssetInstallationStatus.REMOVED, removed.status());
        assertEquals("TECH-42", removed.removedBy());
        assertThrows(IllegalStateException.class, () -> removed.markRemoved(NOW, "X"));
    }
}
