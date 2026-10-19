package tech.kayys.numou.identity.application.port;

import io.smallrye.mutiny.Uni;
import tech.kayys.numou.identity.application.security.Principal;

public interface CurrentPrincipalPort {
    Uni<Principal> current();
}
