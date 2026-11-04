package tech.kayys.numou.applicationplatform.application.configuration;

import tech.kayys.numou.applicationplatform.application.installation.InstallationActor;
import tech.kayys.numou.applicationplatform.domain.installation.ApplicationId;

import java.util.concurrent.CompletionStage;

/** Host authorization boundary; implementations must verify tenant ownership and active installation policy. */
@FunctionalInterface
public interface ApplicationConfigurationAuthorizationPort {
    CompletionStage<Void> requireCanConfigure(InstallationActor actor, ApplicationId applicationId);
}
