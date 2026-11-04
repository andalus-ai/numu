package tech.kayys.numou.applicationplatform.application.navigation;

import org.junit.jupiter.api.Test;
import tech.kayys.numou.applicationplatform.application.installation.*;
import tech.kayys.numou.applicationplatform.domain.installation.*;
import tech.kayys.numou.foundation.domain.tenant.TenantId;
import tech.kayys.numou.plugin.application.manifest.ApplicationManifest;
import tech.kayys.numou.plugin.application.manifest.NavigationContribution;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.*;

class GetApplicationNavigationHandlerTest {
    private static final String DIGEST = "a".repeat(64);
    private static final TenantId TENANT = TenantId.of(
            UUID.fromString("33333333-3333-3333-3333-333333333333"));

    @Test
    void exposesOnlyAuthorizedNavigationFromEnabledVerifiedPackagesInStableOrder() {
        var crm = enabled("com.example.crm");
        var commerce = enabled("com.example.commerce");
        var suspended = enabled("com.example.suspended");
        suspended.suspend("user:admin", Instant.parse("2026-11-04T00:00:01Z"));
        var actor = new InstallationActor(TENANT, "user:alice", "corr");
        var packageLookups = new AtomicInteger();

        ApplicationInstallationQueryPort installations = tenant -> {
            assertThat(tenant).isEqualTo(TENANT);
            return CompletableFuture.completedFuture(List.of(commerce, suspended, crm));
        };
        ApplicationPackageCatalogPort packages = (id, version) -> {
            packageLookups.incrementAndGet();
            return CompletableFuture.completedFuture(Optional.of(new ValidatedApplicationPackage(
                    manifest(id.value(), List.of(
                            new NavigationContribution("home", "Home", "/apps/home", "app.read"),
                            new NavigationContribution("admin", "Admin", "/apps/admin", "app.admin"))),
                    DIGEST)));
        };
        ApplicationNavigationAuthorizationPort policy = (who, permission) -> {
            assertThat(who).isEqualTo(actor);
            return CompletableFuture.completedFuture(permission.equals("app.read"));
        };

        var handler = new GetApplicationNavigationHandler(() -> actor, installations, packages, policy);
        var result = handler.handle().toCompletableFuture().join();

        assertThat(result).extracting(ApplicationNavigationEntry::stableKey)
                .containsExactly("com.example.commerce:home", "com.example.crm:home");
        assertThat(packageLookups).hasValue(2);
        assertThat(result).allSatisfy(item -> assertThat(item.route()).startsWith("/apps/"));
    }

    @Test
    void rejectsCatalogPackageWhoseDigestDiffersFromInstalledArtifact() {
        var installation = enabled("com.example.tampered");
        var handler = new GetApplicationNavigationHandler(
                () -> new InstallationActor(TENANT, "user:alice", ""),
                tenant -> CompletableFuture.completedFuture(List.of(installation)),
                (id, version) -> CompletableFuture.completedFuture(Optional.of(
                        new ValidatedApplicationPackage(manifest(id.value(), List.of()), "b".repeat(64)))),
                (actor, permission) -> CompletableFuture.completedFuture(true));

        assertThatThrownBy(() -> handler.handle().toCompletableFuture().join())
                .hasRootCauseInstanceOf(SecurityException.class);
    }

    @Test
    void rejectsCrossTenantInstallationReturnedByQueryPort() {
        var otherTenant = TenantId.generate();
        var installation = enabled(otherTenant, "com.example.other");
        var handler = new GetApplicationNavigationHandler(
                () -> new InstallationActor(TENANT, "user:alice", ""),
                tenant -> CompletableFuture.completedFuture(List.of(installation)),
                (id, version) -> CompletableFuture.completedFuture(Optional.empty()),
                (actor, permission) -> CompletableFuture.completedFuture(true));

        assertThatThrownBy(() -> handler.handle().toCompletableFuture().join())
                .hasRootCauseInstanceOf(SecurityException.class);
    }

    private static ApplicationManifest manifest(String id, List<NavigationContribution> navigation) {
        return new ApplicationManifest(id, "1.0.0", "com.example.publisher", "Example",
                List.of(), List.of(), Set.of("app.read", "app.admin"), navigation);
    }

    private static ApplicationInstallation enabled(String id) {
        return enabled(TENANT, id);
    }

    private static ApplicationInstallation enabled(TenantId tenant, String id) {
        Instant now = Instant.parse("2026-11-04T00:00:00Z");
        var app = new ApplicationId(id);
        var installation = ApplicationInstallation.request(tenant, app, new PackageVersion("1.0.0"),
                DIGEST, "user:installer", now);
        installation.beginValidation("user:worker", now.plusSeconds(1));
        installation.beginProvisioning("user:worker", now.plusSeconds(2));
        installation.completeProvisioning("user:worker", now.plusSeconds(3));
        installation.enable("user:admin", now.plusSeconds(4));
        return installation;
    }
}
