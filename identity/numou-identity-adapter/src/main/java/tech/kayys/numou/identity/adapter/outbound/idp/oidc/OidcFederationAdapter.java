package tech.kayys.numou.identity.adapter.outbound.idp.oidc;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import tech.kayys.numou.cache.Cache;
import tech.kayys.numou.cache.CacheKey;
import tech.kayys.numou.cache.CachePolicy;
import tech.kayys.numou.cache.adapter.CaffeineCache;
import tech.kayys.numou.foundation.application.result.ApplicationError;
import tech.kayys.numou.foundation.application.result.ApplicationErrorException;
import tech.kayys.numou.identity.adapter.outbound.idp.JacksonFederationSettingsParser;
import tech.kayys.numou.identity.application.dto.FederationAuthorizationRequest;
import tech.kayys.numou.identity.application.dto.FederationCallback;
import tech.kayys.numou.identity.application.dto.FederationSettings;
import tech.kayys.numou.identity.application.port.FederatedIdentityProviderPort;
import tech.kayys.numou.identity.domain.idp.IdentityProviderConfig;
import tech.kayys.numou.identity.domain.user.FederatedIdentity;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.Signature;
import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.spec.MGF1ParameterSpec;
import java.security.spec.PSSParameterSpec;
import java.security.spec.RSAPublicKeySpec;
import java.time.Duration;
import java.util.Base64;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * OIDC / OAuth2 authorization-code adapter with PKCE (RFC 7636) and ID-token signature
 * verification against the provider's JWKS.
 *
 * <p>Covers the blueprint's "OIDC/OAuth2 discovery and callback endpoint" flow for Okta,
 * Keycloak, Azure AD / Entra ID and Google:
 * <ol>
 *   <li>{@link #authorizationUrl} - discovery (cached 10 min) + authorization URL with
 *       {@code code_challenge}/{@code state}/{@code nonce};</li>
 *   <li>{@link #exchangeCode} - token exchange (with {@code code_verifier}), then ID token
 *       verification: {@code iss}/{@code aud}/{@code exp}/{@code nonce} claims and an
 *       RS or PS signature check against the JWKS.</li>
 * </ol>
 *
 * <p>Limitations (documented deliberately): ES* (ECDSA) ID tokens and plain OAuth2 providers
 * that return no {@code id_token} are rejected - the first would need ECDSA JWS support, the
 * second needs a UserInfo endpoint round-trip. SAML and LDAP use their own adapters.
 */
@ApplicationScoped
public class OidcFederationAdapter implements FederatedIdentityProviderPort {

    private static final String DISCOVERY_PATH = "/.well-known/openid-configuration";
    private static final Duration CLOCK_SKEW = Duration.ofSeconds(60);
    private static final Duration JWKS_TTL_MS = Duration.ofMinutes(10);

    private final ObjectMapper objectMapper;
    private final JacksonFederationSettingsParser settingsParser;
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
    private final ExecutorService executor = Executors.newCachedThreadPool(runnable -> {
        Thread thread = new Thread(runnable, "oidc-http");
        thread.setDaemon(true);
        return thread;
    });

    /** Provider metadata, keyed by issuer - invalidation-safe for 10 minutes. */
    private final Cache<OidcDiscovery> discoveryCache = new CaffeineCache<>(
            "identity.oidc.discovery", new CachePolicy(Duration.ofMinutes(10), Duration.ofSeconds(30), 1_000));

    /** Small hot cache for the JWKS document (re-fetched at most every {@link #JWKS_TTL_MS}). */
    private volatile CachedJwks jwks;

    @Inject
    public OidcFederationAdapter(ObjectMapper objectMapper, JacksonFederationSettingsParser settingsParser) {
        this.objectMapper = Objects.requireNonNull(objectMapper);
        this.settingsParser = Objects.requireNonNull(settingsParser);
    }

    @Override
    public Uni<String> authorizationUrl(IdentityProviderConfig config, FederationAuthorizationRequest request) {
        final FederationSettings settings;
        try {
            settings = settingsParser.parse(config);
        } catch (RuntimeException ex) {
            return failure("FEDERATION_MISCONFIGURED", ex.getMessage());
        }
        if (settings.clientId().isBlank()) {
            return failure("FEDERATION_MISCONFIGURED", "connectionConfigJson must contain clientId");
        }
        if (settings.redirectUri().isBlank()) {
            return failure("FEDERATION_MISCONFIGURED", "connectionConfigJson must contain redirectUri");
        }
        if (!settings.authorizationEndpoint().isBlank()) {
            // Reject non-http(s) authorization endpoints before the browser is ever sent there.
            try {
                requireHttpUrl(settings.authorizationEndpoint());
            } catch (ApplicationErrorException ex) {
                return failure("FEDERATION_MISCONFIGURED", ex.error().message());
            }
        }
        return resolveEndpoints(settings)
                .map(resolved -> buildAuthorizationUrl(resolved, request));
    }

    @Override
    public Uni<FederatedIdentity> exchangeCode(IdentityProviderConfig config, FederationCallback callback) {
        final FederationSettings settings;
        try {
            settings = settingsParser.parse(config);
        } catch (RuntimeException ex) {
            return failure("FEDERATION_MISCONFIGURED", ex.getMessage());
        }
        return resolveEndpoints(settings)
                .chain(resolved -> Uni.createFrom()
                        .<FederatedIdentity>item(() -> doTokenExchange(resolved, callback, config))
                        .runSubscriptionOn(executor));
    }

    /** Fills missing endpoints from the cached OIDC discovery document. */
    private Uni<FederationSettings> resolveEndpoints(FederationSettings settings) {
        boolean explicit = !settings.authorizationEndpoint().isBlank()
                && !settings.tokenEndpoint().isBlank()
                && !settings.jwksUri().isBlank();
        if (explicit) {
            return Uni.createFrom().item(settings);
        }
        if (settings.issuer().isBlank()) {
            return failure("FEDERATION_MISCONFIGURED",
                    "connectionConfigJson needs an issuer (for discovery) or all three explicit endpoints");
        }
        return discoveryCache.getOrLoad(
                        CacheKey.platform("identity.oidc.discovery", settings.issuer()),
                        () -> Uni.createFrom()
                                .item(() -> fetchDiscovery(settings.issuer()))
                                .runSubscriptionOn(executor))
                .map(discovery -> settings.withDiscoveredEndpoints(
                        discovery.authorizationEndpoint(),
                        discovery.tokenEndpoint(),
                        discovery.jwksUri()));
    }

    private String buildAuthorizationUrl(FederationSettings settings, FederationAuthorizationRequest request) {
        String separator = settings.authorizationEndpoint().contains("?") ? "&" : "?";
        return settings.authorizationEndpoint() + separator
                + "response_type=code"
                + "&client_id=" + encode(settings.clientId())
                + "&redirect_uri=" + encode(settings.redirectUri())
                + "&scope=" + encode(String.join(" ", settings.scopes()))
                + "&state=" + encode(request.state())
                + "&nonce=" + encode(request.nonce())
                + "&code_challenge=" + encode(request.codeChallenge())
                + "&code_challenge_method=S256";
    }

    private OidcDiscovery fetchDiscovery(String issuer) {
        String url = (issuer.endsWith("/") ? issuer.substring(0, issuer.length() - 1) : issuer) + DISCOVERY_PATH;
        JsonNode document = getJson(url);
        var discovery = new OidcDiscovery(
                document.path("authorization_endpoint").asText(""),
                document.path("token_endpoint").asText(""),
                document.path("jwks_uri").asText("")
        );
        if (discovery.authorizationEndpoint().isBlank()
                || discovery.tokenEndpoint().isBlank()
                || discovery.jwksUri().isBlank()) {
            throw error("FEDERATION_DISCOVERY_INVALID",
                    "Discovery document at " + url + " is missing authorization_endpoint/token_endpoint/jwks_uri");
        }
        return discovery;
    }

    private JsonNode getJson(String url) {
        try {
            requireHttpUrl(url);
            HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                    .timeout(Duration.ofSeconds(5))
                    .header("Accept", "application/json")
                    .GET()
                    .build();
            HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                throw error("FEDERATION_HTTP_ERROR", "GET " + url + " returned HTTP " + response.statusCode());
            }
            return objectMapper.readTree(response.body());
        } catch (ApplicationErrorException ex) {
            throw ex;
        } catch (Exception ex) {
            throw error("FEDERATION_HTTP_ERROR", "Could not read " + url + ": " + ex.getClass().getSimpleName());
        }
    }

    private FederatedIdentity doTokenExchange(FederationSettings settings, FederationCallback callback,
                                              IdentityProviderConfig config) {
        requireHttpUrl(settings.tokenEndpoint());
        String form = "grant_type=authorization_code"
                + "&code=" + encode(callback.code())
                + "&redirect_uri=" + encode(settings.redirectUri())
                + "&client_id=" + encode(settings.clientId())
                + (settings.clientSecret().isBlank() ? "" : "&client_secret=" + encode(settings.clientSecret()))
                + "&code_verifier=" + encode(callback.codeVerifier());

        HttpResponse<String> response;
        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create(settings.tokenEndpoint()))
                    .timeout(Duration.ofSeconds(10))
                    .header("Content-Type", "application/x-www-form-urlencoded")
                    .header("Accept", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(form))
                    .build();
            response = http.send(request, HttpResponse.BodyHandlers.ofString());
        } catch (Exception ex) {
            throw error("FEDERATION_TOKEN_EXCHANGE", "Token endpoint unreachable: " + ex.getClass().getSimpleName());
        }
        if (response.statusCode() != 200) {
            throw error("FEDERATION_TOKEN_EXCHANGE", "Token endpoint returned HTTP " + response.statusCode());
        }

        final JsonNode tokenResponse;
        try {
            tokenResponse = objectMapper.readTree(response.body());
        } catch (Exception ex) {
            throw error("FEDERATION_TOKEN_EXCHANGE", "Token endpoint returned a non-JSON body");
        }
        if (tokenResponse.has("error")) {
            // error_description can echo request values: report the error code only.
            throw error("FEDERATION_TOKEN_EXCHANGE",
                    "Provider denied the code exchange: " + tokenResponse.path("error").asText("unknown"));
        }

        String idToken = tokenResponse.path("id_token").asText(null);
        if (idToken == null || idToken.isBlank()) {
            throw error("FEDERATION_TOKEN_EXCHANGE", "Provider returned no id_token; OIDC is required");
        }
        JsonNode claims = verifyIdToken(idToken, settings, callback.nonce());
        return toFederatedIdentity(config, claims);
    }

    /** Parses and fully verifies an ID token: signature (RS/PS against JWKS) + standard claims. */
    private JsonNode verifyIdToken(String idToken, FederationSettings settings, String expectedNonce) {
        String[] parts = idToken.split("\\.");
        if (parts.length != 3) {
            throw error("FEDERATION_ID_TOKEN_INVALID", "ID token must have three parts");
        }

        final JsonNode header;
        final JsonNode payload;
        try {
            header = objectMapper.readTree(base64UrlDecode(parts[0]));
            payload = objectMapper.readTree(base64UrlDecode(parts[1]));
        } catch (Exception ex) {
            throw error("FEDERATION_ID_TOKEN_INVALID", "ID token header/payload is not valid base64url JSON");
        }

        verifySignature(parts[0] + "." + parts[1], parts[2], header, settings);

        if (!settings.issuer().isBlank() && !settings.issuer().equals(payload.path("iss").asText())) {
            throw error("FEDERATION_ID_TOKEN_INVALID", "ID token iss does not match the configured issuer");
        }
        if (!audienceMatches(payload.path("aud"), settings.clientId())) {
            throw error("FEDERATION_ID_TOKEN_INVALID", "ID token aud does not contain the configured clientId");
        }
        long expiresAt = payload.path("exp").asLong(0);
        if (expiresAt <= 0 || expiresAt <= (System.currentTimeMillis() / 1000) - CLOCK_SKEW.toSeconds()) {
            throw error("FEDERATION_ID_TOKEN_INVALID", "ID token is expired");
        }
        String nonce = payload.path("nonce").asText(null);
        if (nonce == null || !nonce.equals(expectedNonce)) {
            throw error("FEDERATION_ID_TOKEN_INVALID", "ID token nonce does not match the issued nonce");
        }
        if (payload.path("sub").asText("").isBlank()) {
            throw error("FEDERATION_ID_TOKEN_INVALID", "ID token has no sub claim");
        }
        return payload;
    }

    private void verifySignature(String signingInput, String signatureB64, JsonNode header,
                                 FederationSettings settings) {
        String alg = header.path("alg").asText("");
        final byte[] signature;
        try {
            signature = Base64.getUrlDecoder().decode(signatureB64);
        } catch (IllegalArgumentException ex) {
            throw error("FEDERATION_ID_TOKEN_INVALID", "ID token signature is not base64url");
        }

        try {
            switch (alg) {
                case "RS256", "RS384", "RS512" -> rsaVerify(signingInput, signature,
                        header.path("kid").asText(null), settings, alg.substring(2), false);
                case "PS256", "PS384", "PS512" -> rsaVerify(signingInput, signature,
                        header.path("kid").asText(null), settings, alg.substring(2), true);
                default -> throw error("FEDERATION_ID_TOKEN_ALG",
                        "Unsupported ID token algorithm: " + (alg.isBlank() ? "(none)" : alg));
            }
        } catch (ApplicationErrorException ex) {
            throw ex;
        } catch (Exception ex) {
            throw error("FEDERATION_ID_TOKEN_INVALID", "ID token signature verification failed");
        }
    }

    /** Verifies an RS or PS JWS against the matching RSA JWK from the (cached) JWKS. */
    private void rsaVerify(String signingInput, byte[] signature, String kid, FederationSettings settings,
                           String hashBits, boolean pss) throws Exception {
        JsonNode jwk = findRsaJwk(settings, kid);
        if (jwk == null) {
            throw error("FEDERATION_ID_TOKEN_INVALID", "No usable RSA key in the provider JWKS");
        }

        var keySpec = new RSAPublicKeySpec(
                new java.math.BigInteger(1, Base64.getUrlDecoder().decode(jwk.path("n").asText())),
                new java.math.BigInteger(1, Base64.getUrlDecoder().decode(jwk.path("e").asText()))
        );
        PublicKey publicKey = KeyFactory.getInstance("RSA").generatePublic(keySpec);

        String transformation = pss ? "RSASSA-PSS" : "SHA" + hashBits + "withRSA";
        Signature verifier = Signature.getInstance(transformation);
        if (pss) {
            int saltLength = switch (hashBits) {
                case "256" -> 32;
                case "384" -> 48;
                default -> 64;
            };
            verifier.setParameter(new PSSParameterSpec(
                    "SHA-" + hashBits, "MGF1",
                    "256".equals(hashBits) ? MGF1ParameterSpec.SHA256
                            : "384".equals(hashBits) ? MGF1ParameterSpec.SHA384
                            : MGF1ParameterSpec.SHA512,
                    saltLength, 1));
        }
        verifier.initVerify(publicKey);
        verifier.update(signingInput.getBytes(StandardCharsets.US_ASCII));
        if (!verifier.verify(signature)) {
            throw error("FEDERATION_ID_TOKEN_INVALID", "ID token signature verification failed");
        }
    }

    /** Selects the RSA signing key from the JWKS, preferring {@code kid}, with a 10-minute cache. */
    private JsonNode findRsaJwk(FederationSettings settings, String kid) {
        requireHttpUrl(settings.jwksUri());
        CachedJwks cached = this.jwks;
        long now = System.currentTimeMillis();
        if (cached == null || cached.fetchedAtMs() + JWKS_TTL_MS.toMillis() < now
                || !cached.uri().equals(settings.jwksUri())) {
            JsonNode document = getJson(settings.jwksUri());
            this.jwks = new CachedJwks(settings.jwksUri(), document, now);
            cached = this.jwks;
        }

        JsonNode keys = cached.document().path("keys");
        if (!keys.isArray()) {
            return null;
        }
        JsonNode singleRsa = null;
        for (JsonNode key : keys) {
            if (!"RSA".equals(key.path("kty").asText())) {
                continue;
            }
            String use = key.path("use").asText("sig");
            if (!"sig".equals(use)) {
                continue;
            }
            if (kid != null && kid.equals(key.path("kid").asText(null))) {
                return key;
            }
            if (singleRsa == null) {
                singleRsa = key;
            }
        }
        // No kid on the token: accept the provider's single signing key.
        // A kid that matches nothing means a rotated/unknown key - reject (fail closed).
        return kid == null ? singleRsa : null;
    }

    private static boolean audienceMatches(JsonNode audience, String clientId) {
        if (audience.isTextual()) {
            return audience.asText().equals(clientId);
        }
        if (audience.isArray()) {
            for (JsonNode value : audience) {
                if (value.asText().equals(clientId)) {
                    return true;
                }
            }
        }
        return false;
    }

    private FederatedIdentity toFederatedIdentity(IdentityProviderConfig config, JsonNode claims) {
        String subject = claims.path("sub").asText();
        String email = claims.path("email").asText("");
        String displayName = claims.path("name").asText("");
        if (displayName.isBlank()) {
            String given = claims.path("given_name").asText("");
            String family = claims.path("family_name").asText("");
            displayName = (given + " " + family).trim();
        }

        Set<String> groups = new LinkedHashSet<>();
        for (String claimName : new String[] { "groups", "roles", "memberOf" }) {
            JsonNode node = claims.path(claimName);
            if (node.isArray()) {
                node.forEach(value -> {
                    if (!value.isNull()) {
                        groups.add(value.asText());
                    }
                });
            }
        }

        var remainingClaims = new java.util.LinkedHashMap<String, String>();
        for (String claimName : new String[] { "iss", "aud", "preferred_username", "email_verified" }) {
            JsonNode node = claims.path(claimName);
            if (node.isTextual() || node.isBoolean() || node.isNumber()) {
                remainingClaims.put(claimName, node.asText());
            }
        }

        return new FederatedIdentity(
                config.id().value().toString(), subject, email,
                displayName.isBlank() ? null : displayName, groups, remainingClaims
        );
    }

    // ------------------------------------------------------------------ helpers

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20");
    }

    private static String base64UrlDecode(String value) {
        return new String(Base64.getUrlDecoder().decode(value), StandardCharsets.UTF_8);
    }

    private static void requireHttpUrl(String url) {
        if (url == null || url.isBlank()) {
            throw error("FEDERATION_MISCONFIGURED", "Provider endpoint is not configured");
        }
        String lower = url.toLowerCase(java.util.Locale.ROOT);
        if (!(lower.startsWith("https://") || lower.startsWith("http://"))) {
            throw error("FEDERATION_MISCONFIGURED", "Provider endpoint must be an absolute http(s) URL");
        }
    }

    private static ApplicationErrorException error(String code, String message) {
        return ApplicationError.of(code, message).toException();
    }

    private static <T> Uni<T> failure(String code, String message) {
        return Uni.createFrom().failure(error(code, message));
    }

    private record OidcDiscovery(String authorizationEndpoint, String tokenEndpoint, String jwksUri) { }

    private record CachedJwks(String uri, JsonNode document, long fetchedAtMs) { }
}
