package tech.kayys.numou.talent.domain.profile;

import org.junit.jupiter.api.Test;

import java.net.URI;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
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

    @Test
    void invalidPresentationUpdateDoesNotPartiallyMutateAggregate() {
        var profile = profile();
        assertThatThrownBy(() -> profile.updatePresentation(
                "New headline", "x".repeat(5_001), "Jakarta"
        )).isInstanceOf(IllegalArgumentException.class);

        assertThat(profile.headline()).isEqualTo("Backend Engineer");
        assertThat(profile.summary()).isNull();
        assertThat(profile.locationLabel()).isNull();
        assertThat(profile.getVersion()).isZero();
    }

    @Test
    void suspendedProfileCannotChangeVisibility() {
        var profile = profile();
        profile.suspend();

        assertThatThrownBy(() -> profile.changeVisibility(ProfileVisibility.PUBLIC))
                .isInstanceOf(IllegalStateException.class);
        assertThat(profile.visibility()).isEqualTo(ProfileVisibility.PRIVATE);
    }


    @Test
    void storesImmutableSkillsAndCareerEvidenceWithoutOwningEmploymentRecords() {
        var profile = profile();
        var skill = new ProfileContent.Skill(UUID.randomUUID(), "Java", "Engineering",
                ProfileContent.SkillLevel.EXPERT, ProfileContent.Verification.SELF_REPORTED);
        var experience = new ProfileContent.Experience(UUID.randomUUID(), "Example Co", "Backend Engineer",
                "Bandung", LocalDate.of(2020, 1, 1), null, true, "Distributed systems");
        var education = new ProfileContent.Education(UUID.randomUUID(), "Example University", "BSc",
                "Computer Science", LocalDate.of(2015, 1, 1), LocalDate.of(2019, 1, 1), null);
        var credential = new ProfileContent.Credential(UUID.randomUUID(), "Cloud Certificate", "Issuer",
                LocalDate.of(2024, 1, 1), LocalDate.of(2027, 1, 1), UUID.randomUUID(),
                ProfileContent.Verification.ISSUER_VERIFIED);
        var portfolio = new ProfileContent.PortfolioItem(UUID.randomUUID(), "Project", "Case study",
                URI.create("https://example.com/project"), null);

        profile.replaceSkills(List.of(skill));
        profile.replaceExperiences(List.of(experience));
        profile.replaceEducation(List.of(education));
        profile.replaceCredentials(List.of(credential));
        profile.replacePortfolio(List.of(portfolio));

        assertThat(profile.skills()).containsExactly(skill);
        assertThat(profile.experiences()).containsExactly(experience);
        assertThat(profile.education()).containsExactly(education);
        assertThat(profile.credentials()).containsExactly(credential);
        assertThat(profile.portfolio()).containsExactly(portfolio);
        assertThatThrownBy(() -> profile.skills().clear()).isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void rejectsInvalidCareerDatesAndInsecurePortfolioLinks() {
        assertThatThrownBy(() -> new ProfileContent.Experience(UUID.randomUUID(), "Employer", "Role",
                null, LocalDate.of(2024, 1, 1), LocalDate.of(2023, 1, 1), false, null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ProfileContent.Experience(UUID.randomUUID(), "Employer", "Role",
                null, LocalDate.of(2024, 1, 1), LocalDate.of(2024, 2, 1), true, null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ProfileContent.PortfolioItem(UUID.randomUUID(), "Unsafe", null,
                URI.create("http://example.com"), null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ProfileContent.PortfolioItem(UUID.randomUUID(), "Missing", null,
                null, null)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void profileSectionReplacementIsAtomicAndRejectsDuplicateIdsAndOversizedLists() {
        var profile = profile();
        var existing = new ProfileContent.Skill(UUID.randomUUID(), "Java", null,
                ProfileContent.SkillLevel.ADVANCED, ProfileContent.Verification.SELF_REPORTED);
        profile.replaceSkills(List.of(existing));
        long version = profile.getVersion();

        var duplicate = new ProfileContent.Skill(existing.id(), "Kotlin", null,
                ProfileContent.SkillLevel.INTERMEDIATE, ProfileContent.Verification.SELF_REPORTED);
        assertThatThrownBy(() -> profile.replaceSkills(List.of(existing, duplicate)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> profile.replaceSkills(java.util.Collections.nCopies(
                TalentProfile.MAX_PROFILE_ITEMS + 1, existing)))
                .isInstanceOf(IllegalArgumentException.class);

        assertThat(profile.skills()).containsExactly(existing);
        assertThat(profile.getVersion()).isEqualTo(version);
    }

    @Test
    void archivedProfileCannotReplaceCareerSections() {
        var profile = profile();
        profile.archive();
        assertThatThrownBy(() -> profile.replaceSkills(List.of()))
                .isInstanceOf(IllegalStateException.class);
    }

    private TalentProfile profile() {
        return TalentProfile.create(
                TalentProfileId.generate(), tenantId, person, "Backend Engineer", null, null
        );
    }
}
