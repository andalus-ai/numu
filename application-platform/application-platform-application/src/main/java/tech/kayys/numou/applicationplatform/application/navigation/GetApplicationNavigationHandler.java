package tech.kayys.numou.applicationplatform.application.navigation;

import tech.kayys.numou.applicationplatform.application.installation.*;
import tech.kayys.numou.applicationplatform.domain.installation.ApplicationInstallation;
import tech.kayys.numou.plugin.application.manifest.NavigationContribution;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

/**
 * Resolves navigation from enabled, publisher-verified application packages.
 * Application manifests are metadata only; this handler never loads package code.
 */
public final class GetApplicationNavigationHandler {
    private final ApplicationInstallationActorProvider actors;
    private final ApplicationInstallationQueryPort installations;
    private final ApplicationPackageCatalogPort packages;
    private final ApplicationNavigationAuthorizationPort authorization;

    public GetApplicationNavigationHandler(
            ApplicationInstallationActorProvider actors,
            ApplicationInstallationQueryPort installations,
            ApplicationPackageCatalogPort packages,
            ApplicationNavigationAuthorizationPort authorization) {
        this.actors = Objects.requireNonNull(actors, "actors");
        this.installations = Objects.requireNonNull(installations, "installations");
        this.packages = Objects.requireNonNull(packages, "packages");
        this.authorization = Objects.requireNonNull(authorization, "authorization");
    }

    public CompletionStage<List<ApplicationNavigationEntry>> handle() {
        final InstallationActor actor = Objects.requireNonNull(actors.currentActor(), "verified actor context");
        return installations.findEnabled(actor.tenantId()).thenCompose(enabled -> {
            List<ApplicationInstallation> ordered = enabled.stream()
                    .filter(Objects::nonNull)
                    .sorted(Comparator.comparing(i -> i.applicationId().value()))
                    .toList();
            List<CompletableFuture<List<ApplicationNavigationEntry>>> applicationReads = ordered.stream()
                    .map(installation -> resolveInstallation(actor, installation).toCompletableFuture())
                    .toList();
            return CompletableFuture.allOf(applicationReads.toArray(CompletableFuture[]::new))
                    .thenApply(ignored -> applicationReads.stream().flatMap(f -> f.join().stream())
                            .sorted(Comparator.comparing(ApplicationNavigationEntry::applicationId)
                                    .thenComparing(ApplicationNavigationEntry::contributionId))
                            .toList());
        });
    }

    private CompletionStage<List<ApplicationNavigationEntry>> resolveInstallation(
            InstallationActor actor, ApplicationInstallation installation) {
        if (!installation.tenantId().equals(actor.tenantId()))
            return CompletableFuture.failedFuture(new SecurityException("Installation query returned another tenant"));
        if (installation.status() != tech.kayys.numou.applicationplatform.domain.installation.ApplicationInstallationStatus.ENABLED)
            return CompletableFuture.completedFuture(List.of());

        return packages.findValidatedPackage(installation.applicationId(), installation.packageVersion())
                .thenCompose(found -> {
                    ValidatedApplicationPackage validated = found.orElseThrow(
                            () -> new IllegalStateException("Enabled installation has no verified package manifest"));
                    if (!validated.applicationId().equals(installation.applicationId())
                            || !validated.version().equals(installation.packageVersion())
                            || !validated.sha256Digest().equals(installation.packageDigest()))
                        return CompletableFuture.failedFuture(
                                new SecurityException("Installed package identity or digest does not match catalog"));
                    List<CompletableFuture<ApplicationNavigationEntry>> visible = validated.manifest().navigation()
                            .stream()
                            .map(item -> authorizeAndMap(actor, installation, item).toCompletableFuture())
                            .toList();
                    return CompletableFuture.allOf(visible.toArray(CompletableFuture[]::new))
                            .thenApply(ignored -> visible.stream().map(CompletableFuture::join)
                                    .filter(Objects::nonNull).toList());
                });
    }

    private CompletionStage<ApplicationNavigationEntry> authorizeAndMap(
            InstallationActor actor, ApplicationInstallation installation, NavigationContribution item) {
        return authorization.hasPermission(actor, item.requiredPermission()).thenApply(allowed -> {
            if (!Boolean.TRUE.equals(allowed)) return null;
            return new ApplicationNavigationEntry(installation.applicationId().value(),
                    installation.packageVersion().value(), item.id(), item.label(), item.route(),
                    item.requiredPermission());
        });
    }
}
