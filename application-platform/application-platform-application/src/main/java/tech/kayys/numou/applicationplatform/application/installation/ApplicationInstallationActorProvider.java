package tech.kayys.numou.applicationplatform.application.installation;

/** Resolves tenant and actor from a verified host security context. */
@FunctionalInterface
public interface ApplicationInstallationActorProvider {
    InstallationActor currentActor();
}
