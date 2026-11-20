package tech.kayys.numou.socialgraph.domain;

import org.junit.jupiter.api.Test;
import tech.kayys.numou.ecosystem.domain.party.PartyId;
import tech.kayys.numou.foundation.domain.tenant.TenantId;

import java.time.Instant;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SocialRelationshipLifecycleTest {
    private static final Instant NOW = Instant.parse("2026-11-05T00:00:00Z");
    private final TenantId tenant = TenantId.generate();
    private final PartyId source = PartyId.generate();
    private final PartyId target = PartyId.generate();

    @Test
    void createsDirectedRelationshipWithImmutableProvenanceAndMetadata() {
        var metadata = Map.of("source", "directory-import", "campaign", "community-2026");
        SocialRelationship relationship = create(SocialRelationshipType.FOLLOW, NOW, null, metadata);

        assertThat(relationship.type()).isEqualTo(SocialRelationshipType.FOLLOW);
        assertThat(relationship.sourcePartyId()).isEqualTo(source);
        assertThat(relationship.targetPartyId()).isEqualTo(target);
        assertThat(relationship.version()).isZero();
        assertThat(relationship.isEffectiveAt(NOW)).isTrue();
        assertThat(relationship.metadata()).containsAllEntriesOf(metadata);
        assertThatThrownBy(() -> relationship.metadata().put("new", "value"))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void scheduledAndExpiringRelationshipsRespectHalfOpenValidityWindow() {
        Instant start = NOW.plusSeconds(60);
        Instant end = start.plusSeconds(60);
        SocialRelationship relationship = create(SocialRelationshipType.MUTE, start, end, Map.of());

        assertThat(relationship.isEffectiveAt(NOW)).isFalse();
        assertThat(relationship.isEffectiveAt(start)).isTrue();
        assertThat(relationship.isEffectiveAt(end.minusNanos(1))).isTrue();
        assertThat(relationship.isEffectiveAt(end)).isFalse();
    }

    @Test
    void removalIsSourceOwnedAndOptimisticallyVersioned() {
        SocialRelationship relationship = create(SocialRelationshipType.BLOCK, NOW, null, Map.of());

        assertThatThrownBy(() -> relationship.remove(target, NOW.plusSeconds(1)))
                .isInstanceOf(IllegalArgumentException.class);

        SocialRelationship removed = relationship.remove(source, NOW.plusSeconds(1));
        assertThat(removed.status()).isEqualTo(SocialRelationshipStatus.REMOVED);
        assertThat(removed.removedAt()).isEqualTo(NOW.plusSeconds(1));
        assertThat(removed.version()).isEqualTo(1);
        assertThat(removed.isEffectiveAt(NOW.plusSeconds(2))).isFalse();
        assertThatThrownBy(() -> removed.remove(source, NOW.plusSeconds(2)))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void rejectsSelfRelationshipsAndInvalidValidityWindows() {
        assertThatThrownBy(() -> SocialRelationship.create(
                SocialRelationshipId.generate(), tenant, SocialRelationshipType.FOLLOW,
                source, source, NOW, NOW, null, "manual", Map.of()))
                .isInstanceOf(IllegalArgumentException.class);

        assertThatThrownBy(() -> create(SocialRelationshipType.FOLLOW, NOW.minusSeconds(1), null, Map.of()))
                .isInstanceOf(IllegalArgumentException.class);

        assertThatThrownBy(() -> create(SocialRelationshipType.FOLLOW, NOW, NOW, Map.of()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsUnboundedOrMalformedMetadataAndMissingProvenance() {
        assertThatThrownBy(() -> SocialRelationship.create(
                SocialRelationshipId.generate(), tenant, SocialRelationshipType.FOLLOW,
                source, target, NOW, NOW, null, " ", Map.of()))
                .isInstanceOf(IllegalArgumentException.class);

        assertThatThrownBy(() -> SocialRelationship.create(
                SocialRelationshipId.generate(), tenant, SocialRelationshipType.FOLLOW,
                source, target, NOW, NOW, null, "manual", Map.of("bad", "x".repeat(513))))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private SocialRelationship create(
            SocialRelationshipType type, Instant validFrom, Instant validUntil, Map<String, String> metadata) {
        return SocialRelationship.create(SocialRelationshipId.generate(), tenant, type,
                source, target, NOW, validFrom, validUntil, "manual", metadata);
    }
}
