package tech.kayys.numou.applicationplatform.application.installation;

public final class ApplicationPackageNotFoundException extends IllegalArgumentException {
    public ApplicationPackageNotFoundException(String applicationId, String version) {
        super("Validated application package not found: " + applicationId + "@" + version);
    }
}
