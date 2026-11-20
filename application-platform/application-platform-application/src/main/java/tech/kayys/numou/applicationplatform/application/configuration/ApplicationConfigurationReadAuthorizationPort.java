package tech.kayys.numou.applicationplatform.application.configuration;

import tech.kayys.numou.applicationplatform.application.installation.InstallationActor;
import tech.kayys.numou.applicationplatform.domain.installation.ApplicationId;

import java.util.concurrent.CompletionStage;

/** Host authorization boundary for reading configuration and its revision history. */
@FunctionalInterface
public interface ApplicationConfigurationReadAuthorizationPort {
    CompletionStage<Void> requireCanRead(InstallationActor actor, ApplicationId applicationId);
}
