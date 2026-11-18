package tech.kayys.numou.applicationplatform.application.installation;

import tech.kayys.numou.applicationplatform.domain.installation.ApplicationId;
import java.util.concurrent.CompletionStage;

/** Host adapter enforces application-install permission and applicable subscription entitlement. */
@FunctionalInterface
public interface ApplicationInstallationAuthorizationPort {
    CompletionStage<Void> requireCanInstall(InstallationActor actor, ApplicationId applicationId);
}
