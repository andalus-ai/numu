package tech.kayys.syirkah.identity.application.port;

import io.smallrye.mutiny.Uni;
import tech.kayys.syirkah.identity.application.security.Principal;

public interface AuthenticationPort {
    Uni<Principal> authenticate(String credential);
}
