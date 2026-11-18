package tech.kayys.numou.applicationplatform.domain.installation;

import java.util.Objects;
import java.util.UUID;

public record ApplicationInstallationId(UUID value) {
    public ApplicationInstallationId { Objects.requireNonNull(value, "installation ID is required"); }
    public static ApplicationInstallationId generate() { return new ApplicationInstallationId(UUID.randomUUID()); }
}
