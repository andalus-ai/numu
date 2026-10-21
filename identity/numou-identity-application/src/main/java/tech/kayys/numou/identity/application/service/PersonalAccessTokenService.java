package tech.kayys.numou.identity.application.service;

import io.smallrye.mutiny.Uni;
import tech.kayys.numou.cache.Cache;
import tech.kayys.numou.cache.CacheKey;
import tech.kayys.numou.foundation.application.result.ApplicationError;
import tech.kayys.numou.foundation.application.transaction.UnitOfWork;
import tech.kayys.numou.foundation.domain.time.DomainClock;
import tech.kayys.numou.identity.application.dto.CreatePatRequest;
import tech.kayys.numou.identity.application.dto.CreatePatResponse;
import tech.kayys.numou.identity.application.dto.PatResponse;
import tech.kayys.numou.identity.application.port.IdentityOutboxPort;
import tech.kayys.numou.identity.application.port.PersonalAccessTokenRepository;
import tech.kayys.numou.identity.domain.token.PersonalAccessToken;
import tech.kayys.numou.identity.domain.token.PersonalAccessTokenId;
import tech.kayys.numou.identity.domain.token.TokenStatus;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Supplier;

public class PersonalAccessTokenService {

    /** L1 cache namespace for validated token hashes (blueprint: PAT hash cache). */
    public static final String VALIDATION_CACHE_NAMESPACE = "identity.pat.validation";

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final String TOKEN_PREFIX = "pat_";

    private final PersonalAccessTokenRepository patRepository;
    private final IdentityOutboxPort outbox;
    private final UnitOfWork unitOfWork;
    private final DomainClock clock;
    private final Cache<PatResponse> validationCache;

    public PersonalAccessTokenService(
            PersonalAccessTokenRepository patRepository,
            IdentityOutboxPort outbox,
            UnitOfWork unitOfWork,
            DomainClock clock
    ) {
        this(patRepository, outbox, unitOfWork, clock, null);
    }

    /**
     * @param validationCache L1 cache of validated token hashes; {@code null} disables caching.
     *                        Its TTL bounds how long a token can keep validating after a revocation
     *                        that happened on another instance - keep it short. Only cache hits
     *                        skip the {@code last_used_at} write, which turns that column into a
     *                        sampled activity signal rather than an exact counter.
     */
    public PersonalAccessTokenService(
            PersonalAccessTokenRepository patRepository,
            IdentityOutboxPort outbox,
            UnitOfWork unitOfWork,
            DomainClock clock,
            Cache<PatResponse> validationCache
    ) {
        this.patRepository = Objects.requireNonNull(patRepository);
        this.outbox = Objects.requireNonNull(outbox);
        this.unitOfWork = Objects.requireNonNull(unitOfWork);
        this.clock = Objects.requireNonNull(clock);
        this.validationCache = validationCache;
    }

    public Uni<CreatePatResponse> issue(CreatePatRequest request) {
        Objects.requireNonNull(request, "request cannot be null");
        String rawSecret = generateRawSecret();
        String rawToken = TOKEN_PREFIX + rawSecret;
        String displayPrefix = TOKEN_PREFIX + rawSecret.substring(0, Math.min(8, rawSecret.length()));
        String tokenHash = sha256Hex(rawToken);

        PersonalAccessTokenId tokenId = PersonalAccessTokenId.newId();
        var pat = PersonalAccessToken.issue(
                tokenId,
                request.principalType(),
                request.principalId(),
                request.label(),
                tokenHash,
                displayPrefix,
                request.expiresAt(),
                clock.now()
        );

        return unitOfWork.execute(() ->
                patRepository.save(pat)
                        .chain(() -> outbox.append(null, pat.pullDomainEvents()))
                        .replaceWith(new CreatePatResponse(
                                pat.id().value(),
                                pat.label(),
                                rawToken,
                                pat.displayPrefix(),
                                pat.expiresAt()
                        ))
        );
    }

    public Uni<Void> revokeOwned(UUID tokenId, UUID actorId) {
        Objects.requireNonNull(tokenId, "tokenId cannot be null");
        Objects.requireNonNull(actorId, "actorId cannot be null");

        return unitOfWork.execute(() ->
                patRepository.findById(PersonalAccessTokenId.of(tokenId))
                        .chain(optional -> {
                            if (optional.isEmpty()) {
                                return Uni.createFrom().failure(ApplicationError.of(
                                        "PAT_NOT_FOUND", "Personal access token not found"
                                ).toException());
                            }
                            var pat = optional.get();
                            if (!actorId.equals(pat.principalId())) {
                                return Uni.createFrom().failure(ApplicationError.of(
                                        "FORBIDDEN", "Cannot revoke a token belonging to another principal"
                                ).toException());
                            }
                            if (pat.status() == TokenStatus.REVOKED) {
                                return Uni.createFrom().failure(ApplicationError.of(
                                        "PAT_ALREADY_REVOKED", "Token is already revoked"
                                ).toException());
                            }
                            pat.revoke(actorId.toString(), clock.now());
                            return patRepository.save(pat)
                                    .chain(() -> outbox.append(null, pat.pullDomainEvents()))
                                    // Local revocation must not leave the hash cached and valid.
                                    .chain(() -> invalidateValidation(pat.tokenHash()));
                        })
        );
    }

    public Uni<PatResponse> validateAndTouch(String rawToken) {
        if (rawToken == null || rawToken.isBlank()) {
            return Uni.createFrom().failure(ApplicationError.of(
                    "INVALID_TOKEN", "Token cannot be blank"
            ).toException());
        }
        String hash = sha256Hex(rawToken);
        Supplier<Uni<PatResponse>> loader = () -> loadAndTouch(hash);
        if (validationCache == null) {
            return loader.get();
        }
        // Failures (unknown/expired/revoked tokens) are never cached by the Cache contract.
        return validationCache.getOrLoad(CacheKey.platform(VALIDATION_CACHE_NAMESPACE, hash), loader);
    }

    private Uni<PatResponse> loadAndTouch(String hash) {
        return unitOfWork.execute(() ->
                patRepository.findByTokenHash(hash)
                        .chain(optional -> {
                            if (optional.isEmpty()) {
                                return Uni.createFrom().failure(ApplicationError.of(
                                        "PAT_NOT_FOUND", "Personal access token not found"
                                ).toException());
                            }
                            var pat = optional.get();
                            if (!pat.isActive(clock.now())) {
                                return Uni.createFrom().failure(ApplicationError.of(
                                        "PAT_NOT_ACTIVE", "Personal access token is not active or has expired"
                                ).toException());
                            }
                            pat.touch(clock.now());
                            return patRepository.save(pat).replaceWith(PatResponse.from(pat));
                        })
        );
    }

    private Uni<Void> invalidateValidation(String tokenHash) {
        if (validationCache == null) {
            return Uni.createFrom().voidItem();
        }
        return validationCache.invalidate(CacheKey.platform(VALIDATION_CACHE_NAMESPACE, tokenHash));
    }

    public Uni<List<PatResponse>> listForPrincipal(String principalType, UUID principalId) {
        return patRepository.findByPrincipal(principalType, principalId)
                .map(list -> list.stream().map(PatResponse::from).toList());
    }

    private static String generateRawSecret() {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    public static String sha256Hex(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(value.getBytes()));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }
}
