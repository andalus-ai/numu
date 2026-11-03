package tech.kayys.numou.applicationplatform.application.installation;

import tech.kayys.numou.applicationplatform.domain.installation.ApplicationId;
import tech.kayys.numou.applicationplatform.domain.installation.PackageVersion;
import java.util.Objects;

public record RequestApplicationInstallationCommand(ApplicationId applicationId,
        PackageVersion packageVersion, String idempotencyKey) {
    public RequestApplicationInstallationCommand {
        Objects.requireNonNull(applicationId); Objects.requireNonNull(packageVersion);
        if (idempotencyKey == null || idempotencyKey.trim().length() < 16 || idempotencyKey.trim().length() > 200)
            throw new IllegalArgumentException("Idempotency key must contain 16 to 200 characters");
        idempotencyKey = idempotencyKey.trim();
    }
}
