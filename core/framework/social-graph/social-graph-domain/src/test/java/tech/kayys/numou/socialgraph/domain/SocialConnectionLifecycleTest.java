package tech.kayys.numou.socialgraph.domain;

import org.junit.jupiter.api.Test;
import tech.kayys.numou.ecosystem.domain.party.PartyId;
import tech.kayys.numou.foundation.domain.tenant.TenantId;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SocialConnectionLifecycleTest {
    private final TenantId tenant = TenantId.generate();
    private final PartyId requester = PartyId.generate();
    private final PartyId recipient = PartyId.generate();
    private final Instant now = Instant.parse("2026-11-04T00:00:00Z");

    @Test
    void requestStartsPendingAndRecipientCanAccept() {
        SocialConnection requested = SocialConnection.request(SocialConnectionId.generate(), tenant, requester, recipient, now);
        SocialConnection accepted = requested.accept(recipient, now.plusSeconds(1));
        assertThat(requested.status()).isEqualTo(SocialConnectionStatus.PENDING);
        assertThat(accepted.status()).isEqualTo(SocialConnectionStatus.ACTIVE);
        assertThat(accepted.version()).isEqualTo(1);
        assertThat(accepted.resolvedAt()).isEqualTo(now.plusSeconds(1));
    }

    @Test
    void onlyRecipientCanAcceptOrReject() {
        SocialConnection requested = SocialConnection.request(SocialConnectionId.generate(), tenant, requester, recipient, now);
        assertThatThrownBy(() -> requested.accept(requester, now.plusSeconds(1))).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> requested.reject(requester, now.plusSeconds(1))).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void onlyRequesterCanCancelPendingRequest() {
        SocialConnection requested = SocialConnection.request(SocialConnectionId.generate(), tenant, requester, recipient, now);
        assertThatThrownBy(() -> requested.cancel(recipient, now.plusSeconds(1))).isInstanceOf(IllegalArgumentException.class);
        assertThat(requested.cancel(requester, now.plusSeconds(1)).status()).isEqualTo(SocialConnectionStatus.CANCELLED);
    }

    @Test
    void eitherParticipantCanRemoveAnActiveConnection() {
        SocialConnection active = SocialConnection.request(SocialConnectionId.generate(), tenant, requester, recipient, now)
                .accept(recipient, now.plusSeconds(1));
        assertThat(active.remove(requester, now.plusSeconds(2)).status()).isEqualTo(SocialConnectionStatus.REMOVED);
        assertThat(active.remove(recipient, now.plusSeconds(2)).status()).isEqualTo(SocialConnectionStatus.REMOVED);
    }

    @Test
    void rejectsSelfConnectionAndInvalidRehydration() {
        assertThatThrownBy(() -> SocialConnection.request(SocialConnectionId.generate(), tenant, requester, requester, now))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> SocialConnection.rehydrate(SocialConnectionId.generate(), tenant, requester, recipient,
                SocialConnectionStatus.ACTIVE, now, now, null, 1)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsTimeRegressionAndTransitionsFromTerminalStates() {
        SocialConnection requested = SocialConnection.request(SocialConnectionId.generate(), tenant, requester, recipient, now);
        assertThatThrownBy(() -> requested.accept(recipient, now.minusSeconds(1))).isInstanceOf(IllegalArgumentException.class);
        SocialConnection rejected = requested.reject(recipient, now.plusSeconds(1));
        assertThatThrownBy(() -> rejected.accept(recipient, now.plusSeconds(2))).isInstanceOf(IllegalStateException.class);
    }
}
