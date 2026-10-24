package tech.kayys.numou.omnichannel.core;

import org.junit.jupiter.api.Test;
import tech.kayys.numou.foundation.domain.tenant.TenantId;
import java.util.Optional;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

class InboxClaimResultTest {
    private static final InboundEventKey KEY = new InboundEventKey(TenantId.of("00000000-0000-0000-0000-000000000001"), "provider-a", "account-1", "event-1");
    private static final String HASH = "a".repeat(64);
    @Test void onlyAcceptedClaimMayBeginProcessing() {
        assertThat(new InboxClaimResult(KEY, InboxClaimDisposition.ACCEPTED, Optional.empty()).mayBeginProcessing()).isTrue();
        assertThat(new InboxClaimResult(KEY, InboxClaimDisposition.DUPLICATE, Optional.of(HASH)).mayBeginProcessing()).isFalse();
        assertThat(new InboxClaimResult(KEY, InboxClaimDisposition.PAYLOAD_CONFLICT, Optional.of(HASH)).mayBeginProcessing()).isFalse();
    }
    @Test void duplicateAndConflictMustExposeStoredDigest() {
        assertThatIllegalArgumentException().isThrownBy(() -> new InboxClaimResult(KEY, InboxClaimDisposition.DUPLICATE, Optional.empty()));
    }
}