package tech.kayys.numou.construction.domain.consultancy;

import org.junit.jupiter.api.Test;
import java.util.UUID;
import static org.assertj.core.api.Assertions.*;

class ConsultancyEngagementTest {
    @Test
    void proposalMustBeSubmittedBeforeActivationAndActiveScopeCannotBeSilentlyRevised() {
        var engagement = newEngagement();

        assertThatThrownBy(engagement::activate).isInstanceOf(IllegalStateException.class);
        engagement.submitProposal();
        engagement.reviseProposal("Revised design consultancy", "Design and supervision services");
        assertThat(engagement.status()).isEqualTo(ConsultancyEngagementStatus.DRAFT);
        assertThat(engagement.scopeRevision()).isEqualTo(2);

        engagement.submitProposal();
        engagement.activate();
        assertThat(engagement.status()).isEqualTo(ConsultancyEngagementStatus.ACTIVE);
        assertThatThrownBy(() -> engagement.reviseProposal("Changed", "Changed scope"))
                .isInstanceOf(IllegalStateException.class);

        engagement.complete();
        assertThat(engagement.status()).isEqualTo(ConsultancyEngagementStatus.COMPLETED);
        assertThatThrownBy(() -> engagement.cancel("No longer needed"))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void clientAndConsultantMustBeDifferentOrganizations() {
        var organization = UUID.randomUUID();

        assertThatThrownBy(() -> ConsultancyEngagement.create(
                UUID.randomUUID(), organization, organization, "ENG-1", "Design", "Design services"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void cancellationRequiresAnAuditableReason() {
        var engagement = newEngagement();

        assertThatThrownBy(() -> engagement.cancel(" "))
                .isInstanceOf(IllegalArgumentException.class);
        engagement.cancel("Client cancelled the opportunity");

        assertThat(engagement.status()).isEqualTo(ConsultancyEngagementStatus.CANCELLED);
        assertThat(engagement.cancellationReason()).isEqualTo("Client cancelled the opportunity");
    }

    @Test
    void persistedAggregateCanBeReconstitutedWithoutChangingItsIdentityOrLifecycle() {
        var original = newEngagement();
        original.submitProposal();
        original.activate();

        var restored = ConsultancyEngagement.reconstitute(
                original.id().value(), original.projectId(), original.clientOrganizationId(),
                original.consultantOrganizationId(), original.engagementNumber(), original.title(),
                original.scopeStatement(), original.scopeRevision(), original.status(),
                original.cancellationReason());

        assertThat(restored.id()).isEqualTo(original.id());
        assertThat(restored.status()).isEqualTo(ConsultancyEngagementStatus.ACTIVE);
        assertThat(restored.scopeRevision()).isEqualTo(original.scopeRevision());
        assertThat(restored.title()).isEqualTo(original.title());
        assertThatThrownBy(() -> restored.reviseProposal("Changed", "Changed scope"))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void reconstitutionRejectsImpossiblePersistedStates() {
        var engagement = newEngagement();

        assertThatThrownBy(() -> ConsultancyEngagement.reconstitute(
                engagement.id().value(), engagement.projectId(), engagement.clientOrganizationId(),
                engagement.consultantOrganizationId(), engagement.engagementNumber(), engagement.title(),
                engagement.scopeStatement(), 0, ConsultancyEngagementStatus.DRAFT, null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> ConsultancyEngagement.reconstitute(
                engagement.id().value(), engagement.projectId(), engagement.clientOrganizationId(),
                engagement.consultantOrganizationId(), engagement.engagementNumber(), engagement.title(),
                engagement.scopeStatement(), 1, ConsultancyEngagementStatus.CANCELLED, null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> ConsultancyEngagement.reconstitute(
                engagement.id().value(), engagement.projectId(), engagement.clientOrganizationId(),
                engagement.consultantOrganizationId(), engagement.engagementNumber(), engagement.title(),
                engagement.scopeStatement(), 1, ConsultancyEngagementStatus.ACTIVE, "stale cancellation"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private ConsultancyEngagement newEngagement() {
        return ConsultancyEngagement.create(
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                "ENG-2026-001",
                "Engineering consultancy",
                "Design review and construction supervision");
    }
}
