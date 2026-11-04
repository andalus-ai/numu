package tech.kayys.numou.talent.domain.profile;

import org.junit.jupiter.api.Test;
import tech.kayys.numou.ecosystem.domain.party.PartyId;
import tech.kayys.numou.ecosystem.domain.party.PartyKind;
import tech.kayys.numou.ecosystem.domain.party.PartyRef;
import tech.kayys.numou.foundation.domain.tenant.TenantId;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TalentProfileTest {

    private final TenantId tenantId = TenantId.generate();
    private final PartyRef person = PartyRef.of(PartyId.generate(), PartyKind.PERSON);

    @Test
    void createsPrivateDraftForCanonicalPersonParty() {
        var profile = TalentProfile.create(
                TalentProfileId.generate(), tenantId, person,
                "Senior Backend Engineer", "Builds reliable distributed systems", "Bandung, Indonesia"
        );

        assertThat(profile.status()).isEqualTo(TalentProfileStatus.DRAFT);
        assertThat(profile.visibility()).isEqualTo(ProfileVisibility.PRIVATE);
        assertThat(profile.subject()).isEqualTo(person);
        assertThat(profile.tenantId()).isEqualTo(tenantId);
        assertThat(profile.pullDomainEvents()).hasSize(1);
    }

    @Test
    void rejectsOrganizationAndUnknownPartyKinds() {
        assertThatThrownBy(() -> TalentProfile.create(
                TalentProfileId.generate(), tenantId,
                PartyRef.of(PartyId.generate(), PartyKind.ORGANIZATION), "Engineer", null, null
        )).isInstanceOf(IllegalArgumentException.class);

        assertThatThrownBy(() -> TalentProfile.create(
                TalentProfileId.generate(), tenantId,
                PartyRef.of(PartyId.generate()), "Engineer", null, null
        )).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void updatesPresentationAndPublishesWithoutChangingIdentity() {
        var profile = profile();
        profile.pullDomainEvents();

        profile.updatePresentation("Platform Engineer", "Designs resilient platforms", "Jakarta");
        profile.changeVisibility(ProfileVisibility.PUBLIC);
        profile.publish();

        assertThat(profile.headline()).isEqualTo("Platform Engineer");
        assertThat(profile.locationLabel()).isEqualTo("Jakarta");
        assertThat(profile.subject()).isEqualTo(person);
        assertThat(profile.status()).isEqualTo(TalentProfileStatus.PUBLISHED);
        assertThat(profile.visibility()).isEqualTo(ProfileVisibility.PUBLIC);
        assertThat(profile.getVersion()).isEqualTo(3);
        assertThat(profile.pullDomainEvents()).extracting("eventType")
                .containsExactly("talent.profile.visibility-changed", "talent.profile.published");
    }

    @Test
    void preventsEditingSuspendedOrArchivedProfiles() {
        var profile = profile();
        profile.suspend();

        assertThatThrownBy(() -> profile.updatePresentation("Engineer", null, null))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(profile::publish).isInstanceOf(IllegalStateException.class);

        profile.archive();
        assertThat(profile.status()).isEqualTo(TalentProfileStatus.ARCHIVED);
        assertThat(profile.visibility()).isEqualTo(ProfileVisibility.PRIVATE);
        assertThatThrownBy(() -> profile.changeVisibility(ProfileVisibility.PUBLIC))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void validatesBoundedPresentationFields() {
        assertThatThrownBy(() -> TalentProfile.create(
                TalentProfileId.generate(), tenantId, person, " ", null, null
        )).isInstanceOf(IllegalArgumentException.class);

        assertThatThrownBy(() -> TalentProfile.create(
                TalentProfileId.generate(), tenantId, person, "x".repeat(161), null, null
        )).isInstanceOf(IllegalArgumentException.class);

        assertThatThrownBy(() -> TalentProfile.create(
                TalentProfileId.generate(), tenantId, person, "Engineer", null, "x".repeat(161)
        )).isInstanceOf(IllegalArgumentException.class);
    }

    private TalentProfile profile() {
        return TalentProfile.create(
                TalentProfileId.generate(), tenantId, person, "Backend Engineer", null, null
        );
    }
}
