package tech.kayys.numou.omnichannel.core;

import org.junit.jupiter.api.Test;
import tech.kayys.numou.foundation.domain.tenant.TenantId;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

class ChannelContextTest {

    @Test
    void normalizesOptionalBranchAndRequiredMetadata() {
        TenantId tenantId = TenantId.of("tenant-1");

        ChannelContext context = ChannelContext.of(
                tenantId, " branch-jkt ", ChannelType.MARKETPLACE, " account-1 ", " corr-123 ");

        assertThat(context.tenantId()).isEqualTo(tenantId);
        assertThat(context.branchId()).contains("branch-jkt");
        assertThat(context.providerAccountId()).isEqualTo("account-1");
        assertThat(context.correlationId()).isEqualTo("corr-123");
    }

    @Test
    void allowsNoBranchForTenantWideChannels() {
        ChannelContext context = ChannelContext.of(
                TenantId.of("tenant-1"), null, ChannelType.PARTNER_API, "account-1", "corr-1");

        assertThat(context.branchId()).isEmpty();
    }

    @Test
    void rejectsMissingTenantAndBlankProviderAccount() {
        assertThatNullPointerException().isThrownBy(() ->
                ChannelContext.of(null, null, ChannelType.WEB, "account-1", "corr-1"));
        assertThatIllegalArgumentException().isThrownBy(() ->
                ChannelContext.of(TenantId.of("tenant-1"), null, ChannelType.WEB, " ", "corr-1"));
    }
}
