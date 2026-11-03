package tech.kayys.numou.applicationplatform.application.installation;

import tech.kayys.numou.applicationplatform.domain.installation.ApplicationInstallation;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.util.HexFormat;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

/** Tenant-safe request orchestration. Provisioning is intentionally asynchronous and outbox-driven. */
public final class RequestApplicationInstallationHandler {
    private final ApplicationInstallationActorProvider actors;
    private final ApplicationInstallationAuthorizationPort authorization;
    private final ApplicationPackageCatalogPort catalog;
    private final ApplicationCapabilityCompatibilityPort compatibility;
    private final ApplicationInstallationStore store;
    private final Clock clock;

    public RequestApplicationInstallationHandler(ApplicationInstallationActorProvider actors,
            ApplicationInstallationAuthorizationPort authorization, ApplicationPackageCatalogPort catalog,
            ApplicationCapabilityCompatibilityPort compatibility, ApplicationInstallationStore store, Clock clock) {
        this.actors = Objects.requireNonNull(actors); this.authorization = Objects.requireNonNull(authorization);
        this.catalog = Objects.requireNonNull(catalog); this.compatibility = Objects.requireNonNull(compatibility);
        this.store = Objects.requireNonNull(store); this.clock = Objects.requireNonNull(clock);
    }

    public CompletionStage<RequestApplicationInstallationResult> handle(RequestApplicationInstallationCommand command) {
        Objects.requireNonNull(command, "command");
        final InstallationActor actor = Objects.requireNonNull(actors.currentActor(), "verified actor context");
        return authorization.requireCanInstall(actor, command.applicationId())
                .thenCompose(ignored -> catalog.findValidatedPackage(command.applicationId(), command.packageVersion()))
                .thenCompose(found -> {
                    if (found.isEmpty()) return CompletableFuture.failedFuture(
                            new ApplicationPackageNotFoundException(command.applicationId().value(), command.packageVersion().value()));
                    ValidatedApplicationPackage pkg = found.get();
                    if (!pkg.applicationId().equals(command.applicationId()) || !pkg.version().equals(command.packageVersion()))
                        return CompletableFuture.failedFuture(new IllegalStateException("Catalog returned a mismatched package descriptor"));
                    return compatibility.assertCompatible(actor.tenantId(), pkg).thenCompose(compatible -> {
                        ApplicationInstallation installation = ApplicationInstallation.request(actor.tenantId(),
                                pkg.applicationId(), pkg.version(), pkg.sha256Digest(), actor.actorId(), clock.instant());
                        return store.createIfAbsent(installation, hash(command.idempotencyKey()),
                                installation.requestedEvent(clock.instant())).thenApply(created -> {
                            if (created.outcome() == ApplicationInstallationStore.CreateResult.Outcome.CONFLICT)
                                throw new ApplicationInstallationConflictException(command.applicationId().value());
                            var saved = created.installation().orElseThrow();
                            return new RequestApplicationInstallationResult(
                                    created.outcome() == ApplicationInstallationStore.CreateResult.Outcome.CREATED
                                            ? RequestApplicationInstallationResult.Outcome.CREATED
                                            : RequestApplicationInstallationResult.Outcome.REPLAYED,
                                    saved);
                        });
                    });
                });
    }

    private static String hash(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 is required by the Java runtime", impossible);
        }
    }
}
