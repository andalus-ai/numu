package tech.kayys.numou.applicationplatform.application.installation;

import tech.kayys.numou.applicationplatform.domain.installation.ApplicationInstallation;
import java.util.Objects;

public record RequestApplicationInstallationResult(Outcome outcome, ApplicationInstallation installation) {
    public enum Outcome { CREATED, REPLAYED }
    public RequestApplicationInstallationResult {
        Objects.requireNonNull(outcome); Objects.requireNonNull(installation);
    }
}
