package tech.kayys.numou.identity.application.service;

import io.smallrye.mutiny.Uni;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tech.kayys.numou.foundation.application.result.ApplicationErrorException;
import tech.kayys.numou.foundation.domain.time.DomainClock;
import tech.kayys.numou.identity.application.dto.CreatePatRequest;
import tech.kayys.numou.identity.application.dto.CreatePatResponse;
import tech.kayys.numou.identity.application.dto.PatResponse;
import tech.kayys.numou.identity.application.port.IdentityOutboxPort;
import tech.kayys.numou.identity.application.port.PersonalAccessTokenRepository;
import tech.kayys.numou.identity.application.support.PassthroughUnitOfWork;
import tech.kayys.numou.identity.application.support.RecordingCache;
import tech.kayys.numou.identity.domain.token.PersonalAccessToken;
import tech.kayys.numou.identity.domain.token.PersonalAccessTokenId;
import tech.kayys.numou.identity.domain.token.TokenStatus;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import static org.junit.jupiter.api.Assertions.*;

class PersonalAccessTokenServiceTest {

    private InMemoryPatRepository patRepository;
    private IdentityOutboxPort outbox;
    private DomainClock clock;
    private PersonalAccessTokenService service;
    private Instant now;

    @BeforeEach
    void setUp() {
        now = Instant.parse("2026-10-20T10:00:00Z");
        clock = () -> now;
        patRepository = new InMemoryPatRepository();
        outbox = (tenantId, events) -> Uni.createFrom().voidItem();
        service = new PersonalAccessTokenService(patRepository, outbox, new PassthroughUnitOfWork(), clock);
    }

    @Test
    void shouldIssueAndValidatePat() {
        UUID userId = UUID.randomUUID();
        CreatePatRequest request = new CreatePatRequest(
                "USER",
                userId,
                "CLI Token",
                now.plus(Duration.ofDays(30))
        );

        CreatePatResponse response = service.issue(request).await().indefinitely();

        assertNotNull(response.id());
        assertTrue(response.token().startsWith("pat_"));
        assertTrue(response.displayPrefix().startsWith("pat_"));
        assertEquals("CLI Token", response.label());

        // Validate token
        PatResponse validated = service.validateAndTouch(response.token()).await().indefinitely();
        assertEquals(response.id(), validated.id());
        assertEquals("CLI Token", validated.label());
        assertEquals(TokenStatus.ACTIVE, validated.status());
        assertEquals(now, validated.lastUsedAt());
    }

    @Test
    void shouldRevokeOwnedToken() {
        UUID userId = UUID.randomUUID();
        CreatePatResponse response = service.issue(new CreatePatRequest(
                "USER", userId, "Desktop App", now.plus(Duration.ofDays(7))
        )).await().indefinitely();

        // Revoke with matching actor
        service.revokeOwned(response.id(), userId).await().indefinitely();

        PersonalAccessToken pat = patRepository.findById(PersonalAccessTokenId.of(response.id()))
                .await().indefinitely().orElseThrow();
        assertEquals(TokenStatus.REVOKED, pat.status());
        assertEquals(now, pat.revokedAt());

        // Validation fails for revoked token
        assertThrows(ApplicationErrorException.class, () ->
                service.validateAndTouch(response.token()).await().indefinitely()
        );
    }

    @Test
    void shouldPreventRevokingAnotherPrincipalsToken() {
        UUID userId = UUID.randomUUID();
        UUID attackerId = UUID.randomUUID();
        CreatePatResponse response = service.issue(new CreatePatRequest(
                "USER", userId, "Secret", now.plus(Duration.ofDays(7))
        )).await().indefinitely();

        assertThrows(ApplicationErrorException.class, () ->
                service.revokeOwned(response.id(), attackerId).await().indefinitely()
        );
    }

    @Test
    void shouldListTokensForPrincipal() {
        UUID userId = UUID.randomUUID();
        service.issue(new CreatePatRequest("USER", userId, "Token 1", null)).await().indefinitely();
        service.issue(new CreatePatRequest("USER", userId, "Token 2", null)).await().indefinitely();

        List<PatResponse> list = service.listForPrincipal("USER", userId).await().indefinitely();
        assertEquals(2, list.size());
    }

    @Test
    void shouldServeValidationFromCacheAndInvalidateItOnRevoke() {
        var cache = new RecordingCache<PatResponse>(
                PersonalAccessTokenService.VALIDATION_CACHE_NAMESPACE);
        service = new PersonalAccessTokenService(
                patRepository, outbox, new PassthroughUnitOfWork(), clock, cache);

        UUID userId = UUID.randomUUID();
        CreatePatResponse issued = service.issue(new CreatePatRequest(
                "USER", userId, "Cached Token", now.plus(Duration.ofDays(30))
        )).await().indefinitely();

        // A warm validation loads once from the repository...
        service.validateAndTouch(issued.token()).await().indefinitely();
        assertEquals(1, cache.loads());

        // ...and every repeat validation with the same raw token skips the repository.
        service.validateAndTouch(issued.token()).await().indefinitely();
        service.validateAndTouch(issued.token()).await().indefinitely();
        assertEquals(1, cache.loads(), "repeat validations must be served from the hash cache");

        // Revocation must evict the hash, so a cached "valid" answer never survives a revoke.
        service.revokeOwned(issued.id(), userId).await().indefinitely();
        assertEquals(1, cache.invalidations());

        assertThrows(ApplicationErrorException.class, () ->
                service.validateAndTouch(issued.token()).await().indefinitely());
    }

    static class InMemoryPatRepository implements PersonalAccessTokenRepository {
        private final Map<PersonalAccessTokenId, PersonalAccessToken> store = new ConcurrentHashMap<>();

        @Override
        public Uni<Void> save(PersonalAccessToken pat) {
            store.put(pat.id(), pat);
            return Uni.createFrom().voidItem();
        }

        @Override
        public Uni<Optional<PersonalAccessToken>> findById(PersonalAccessTokenId id) {
            return Uni.createFrom().item(Optional.ofNullable(store.get(id)));
        }

        @Override
        public Uni<Optional<PersonalAccessToken>> findByTokenHash(String tokenHash) {
            return Uni.createFrom().item(store.values().stream()
                    .filter(p -> p.tokenHash().equals(tokenHash))
                    .findFirst());
        }

        @Override
        public Uni<List<PersonalAccessToken>> findByPrincipal(String principalType, UUID principalId) {
            List<PersonalAccessToken> list = store.values().stream()
                    .filter(p -> p.principalType().equalsIgnoreCase(principalType) && p.principalId().equals(principalId))
                    .toList();
            return Uni.createFrom().item(new ArrayList<>(list));
        }
    }
}
