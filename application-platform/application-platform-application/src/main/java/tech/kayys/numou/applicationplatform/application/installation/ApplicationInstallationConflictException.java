package tech.kayys.numou.applicationplatform.application.installation;

public final class ApplicationInstallationConflictException extends IllegalStateException {
    public ApplicationInstallationConflictException(String applicationId) {
        super("An installation already exists or the idempotency key was reused for a different request: " + applicationId);
    }
}
