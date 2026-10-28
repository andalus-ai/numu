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
