package tech.kayys.numou.foundation.application.identifier;

import io.smallrye.mutiny.Uni;
import tech.kayys.numou.foundation.domain.identifier.IdentifierPolicy;
import tech.kayys.numou.foundation.domain.identifier.IdentifierScope;

import java.util.Optional;

/**
 * Outbound repository port to load configured identifier numbering policies (config03.md §P4-15 #3).
 */
public interface IdentifierPolicyRepository {

    Uni<Optional<IdentifierPolicy>> findActivePolicy(String namespace, IdentifierScope scope);
}
