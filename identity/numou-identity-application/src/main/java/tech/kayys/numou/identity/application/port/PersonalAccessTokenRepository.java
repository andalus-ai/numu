package tech.kayys.numou.identity.application.port;

import io.smallrye.mutiny.Uni;
import tech.kayys.numou.identity.domain.token.PersonalAccessToken;
import tech.kayys.numou.identity.domain.token.PersonalAccessTokenId;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PersonalAccessTokenRepository {

    Uni<Optional<PersonalAccessToken>> findById(PersonalAccessTokenId id);

    Uni<Optional<PersonalAccessToken>> findByTokenHash(String tokenHash);

    Uni<List<PersonalAccessToken>> findByPrincipal(String principalType, UUID principalId);

    Uni<Void> save(PersonalAccessToken pat);
}
