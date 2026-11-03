package tech.kayys.numou.applicationplatform.domain.installation;

import org.junit.jupiter.api.Test;
import tech.kayys.numou.foundation.domain.tenant.TenantId;
import java.time.Instant;
import java.util.UUID;
import static org.assertj.core.api.Assertions.*;

class ApplicationInstallationTest {
    private static final Instant NOW = Instant.parse("2026-11-03T02:00:00Z");
    private static final String DIGEST = "a".repeat(64);
    private ApplicationInstallation requested() {
        return ApplicationInstallation.request(TenantId.of(UUID.fromString("11111111-1111-1111-1111-111111111111")),
                new ApplicationId("com.example.retail"), new PackageVersion("1.2.3"), DIGEST, "user:alice", NOW);
    }

    @Test void followsApprovalAndProvisioningLifecycleWithMonotonicRevision() {
        var installation = requested();
        assertThat(installation.status()).isEqualTo(ApplicationInstallationStatus.REQUESTED);
        assertThat(installation.requestedEvent(NOW).fromStatus()).isEmpty();
        assertThat(installation.beginValidation("user:alice", NOW.plusSeconds(1)).revision()).isEqualTo(2);
        installation.awaitApproval("user:admin", NOW.plusSeconds(2));
        var approved = installation.approve("user:admin", NOW.plusSeconds(3));
        assertThat(approved.fromStatus()).contains(ApplicationInstallationStatus.AWAITING_APPROVAL);
        assertThat(approved.toStatus()).isEqualTo(ApplicationInstallationStatus.PROVISIONING);
        installation.completeProvisioning("system:installer", NOW.plusSeconds(4));
        installation.enable("user:admin", NOW.plusSeconds(5));
        installation.suspend("user:admin", NOW.plusSeconds(6));
        installation.enable("user:admin", NOW.plusSeconds(7));
        assertThat(installation.status()).isEqualTo(ApplicationInstallationStatus.ENABLED);
        assertThat(installation.revision()).isEqualTo(8);
    }

    @Test void approvalGateCannotBeBypassedAndInvalidTransitionsFail() {
        var installation = requested();
        installation.beginValidation("user:alice", NOW);
        installation.awaitApproval("user:admin", NOW.plusSeconds(1));
        assertThatThrownBy(() -> installation.beginProvisioning("system:installer", NOW.plusSeconds(2)))
                .isInstanceOf(IllegalStateException.class);
        installation.approve("user:admin", NOW.plusSeconds(2));
        installation.completeProvisioning("system:installer", NOW.plusSeconds(3));
        assertThatThrownBy(() -> installation.completeProvisioning("system:installer", NOW.plusSeconds(4)))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test void failureCanBeRetriedAndUninstallRetainsPackageProvenance() {
        var installation = requested();
        installation.beginValidation("user:alice", NOW);
        installation.fail("system:installer", NOW.plusSeconds(1), "CAPABILITY_MISSING");
        assertThat(installation.failureCode()).contains("CAPABILITY_MISSING");
        installation.retry("user:admin", NOW.plusSeconds(2));
        installation.beginProvisioning("system:installer", NOW.plusSeconds(3));
        installation.completeProvisioning("system:installer", NOW.plusSeconds(4));
        installation.beginUninstall("user:admin", NOW.plusSeconds(5));
        installation.completeUninstall("system:installer", NOW.plusSeconds(6));
        assertThat(installation.status()).isEqualTo(ApplicationInstallationStatus.UNINSTALLED);
        assertThat(installation.packageDigest()).isEqualTo(DIGEST);
        assertThat(installation.createdBy()).isEqualTo("user:alice");
        assertThat(installation.failureCode()).isEmpty();
        assertThatThrownBy(() -> installation.enable("user:admin", NOW.plusSeconds(7)))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test void rejectsMalformedApplicationIdsVersionsDigestsAndFailureCodes() {
        assertThatThrownBy(() -> new ApplicationId("retail")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new PackageVersion("1.0.0-01")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new PackageVersion("1.0.0-alpha..one")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> ApplicationInstallation.request(TenantId.generate(),
                new ApplicationId("com.example.app"), new PackageVersion("1.0.0"), "not-a-digest", "actor", NOW))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> requested().fail("actor", NOW, "not stable"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
