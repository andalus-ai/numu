package tech.kayys.numou.ecosystem.domain.party;

import org.junit.jupiter.api.Test;
import tech.kayys.numou.foundation.domain.tenant.TenantId;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PartyIdentityAssociationTest {

    @Test
    void createsTenantScopedActiveAssociationWithoutGrantingAuthorization() {
        var linkedAt = Instant.parse("2026-01-01T00:00:00Z");
        var association = PartyIdentityAssociation.link(UUID.randomUUID(),
                TenantId.generate(), PartyId.generate(),
                new IdentitySubjectRef("https://id.example", "subject-123"), linkedAt);

        assertThat(association.isActive()).isTrue();
        assertThat(association.revokedAt()).isNull();
        assertThat(association.identity().issuer()).isEqualTo("https://id.example");
    }

    @Test
    void revokesAssociationImmutably() {
        var linkedAt = Instant.parse("2026-01-01T00:00:00Z");
        var original = PartyIdentityAssociation.link(UUID.randomUUID(),
                TenantId.generate(), PartyId.generate(),
                new IdentitySubjectRef("numou-user", "user-123"), linkedAt);

        var revoked = original.revoke(linkedAt.plusSeconds(60));

        assertThat(original.isActive()).isTrue();
        assertThat(revoked.isActive()).isFalse();
        assertThat(revoked.revokedAt()).isEqualTo(linkedAt.plusSeconds(60));
        assertThat(revoked.id()).isEqualTo(original.id());
    }

    @Test
    void rejectsRevocationBeforeLinkTime() {
        var linkedAt = Instant.parse("2026-01-01T00:00:00Z");
        var association = PartyIdentityAssociation.link(UUID.randomUUID(),
                TenantId.generate(), PartyId.generate(),
                new IdentitySubjectRef("numou-user", "user-123"), linkedAt);

        assertThatThrownBy(() -> association.revoke(linkedAt.minusSeconds(1)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void requiresIssuerAndSubject() {
        assertThatThrownBy(() -> new IdentitySubjectRef(" ", "subject"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new IdentitySubjectRef("issuer", " "))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsRevokedAssociationWithoutRevocationTime() {
        assertThatThrownBy(() -> new PartyIdentityAssociation(UUID.randomUUID(),
                TenantId.generate(), PartyId.generate(),
                new IdentitySubjectRef("issuer", "subject"),
                PartyIdentityAssociationStatus.REVOKED,
                Instant.parse("2026-01-01T00:00:00Z"), null))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
