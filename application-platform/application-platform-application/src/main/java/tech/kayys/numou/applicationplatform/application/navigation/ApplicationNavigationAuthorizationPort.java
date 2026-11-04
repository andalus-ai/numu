package tech.kayys.numou.applicationplatform.application.navigation;

import tech.kayys.numou.applicationplatform.application.installation.InstallationActor;

import java.util.concurrent.CompletionStage;

/** Host policy boundary; a manifest permission request never grants this permission. */
@FunctionalInterface
public interface ApplicationNavigationAuthorizationPort {
    CompletionStage<Boolean> hasPermission(InstallationActor actor, String permission);
}
