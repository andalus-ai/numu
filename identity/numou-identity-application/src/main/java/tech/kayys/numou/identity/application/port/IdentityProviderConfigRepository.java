package tech.kayys.numou.identity.application.port;

import io.smallrye.mutiny.Uni;
import tech.kayys.numou.identity.domain.idp.IdentityProviderConfig;
import tech.kayys.numou.identity.domain.idp.IdentityProviderConfigId;

import java.util.List;
import java.util.Optional;

public interface IdentityProviderConfigRepository {

    Uni<Optional<IdentityProviderConfig>> findById(IdentityProviderConfigId id);

    Uni<Optional<IdentityProviderConfig>> findByName(String name);

    Uni<List<IdentityProviderConfig>> findEnabled();

    Uni<Void> save(IdentityProviderConfig config);
}
