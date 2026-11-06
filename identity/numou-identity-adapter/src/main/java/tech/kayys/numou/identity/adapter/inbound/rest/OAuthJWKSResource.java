package tech.kayys.numou.identity.adapter.inbound.rest;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.HeaderParam;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.io.IOException;
import java.math.BigInteger;
import java.nio.file.Files;
import java.nio.file.Path as FilePath;
import java.security.KeyFactory;
import java.security.MessageDigest;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.RSAPublicKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.Set;

/**
 * Publishes the public signing-key set used by OAuth machine access tokens.
 *
 * The mounted JWKS file is deployment-managed so old and new public keys can
 * overlap during rotation. The active key must match the verifier public key
 * configured for this Identity instance before the document is served.
 */
@ApplicationScoped
@Path("/.well-known/jwks.json")
public class OAuthJWKSResource {
    private static final int MAX_JWKS_BYTES = 64 * 1024;
    private static final int MAX_KEYS = 32;
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final Set<String> PRIVATE_JWK_MEMBERS =
            Set.of("d", "p", "q", "dp", "dq", "qi", "oth", "k");

    private final String jwksLocation;
    private final String activeKeyId;
    private final String publicKeyLocation;

    public OAuthJWKSResource(
            @ConfigProperty(name = "numou.oauth.access-token.jwks.location") String jwksLocation,
            @ConfigProperty(name = "numou.oauth.access-token.signing-key-id") String activeKeyId,
            @ConfigProperty(name = "mp.jwt.verify.publickey.location") String publicKeyLocation) {
        this.jwksLocation = jwksLocation;
        this.activeKeyId = activeKeyId;
        this.publicKeyLocation = publicKeyLocation;
    }

    @GET
    @Produces("application/jwk-set+json")
    public Response jwks(@HeaderParam(HttpHeaders.IF_NONE_MATCH) String ifNoneMatch) {
        try {
            byte[] document = Files.readAllBytes(FilePath.of(jwksLocation));
            if (document.length == 0 || document.length > MAX_JWKS_BYTES) {
                return unavailable();
            }
            RSAPublicKey activePublicKey = readActivePublicKey(FilePath.of(publicKeyLocation));
            validateJwks(document, activeKeyId, activePublicKey);

            String etag = "\"" + HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(document)) + "\"";
            if ("*".equals(ifNoneMatch) || etag.equals(ifNoneMatch)) {
                return Response.notModified().header(HttpHeaders.ETAG, etag)
                        .header(HttpHeaders.CACHE_CONTROL, "public, max-age=30, must-revalidate").build();
            }
            return Response.ok(document, "application/jwk-set+json")
                    .header(HttpHeaders.ETAG, etag)
                    .header(HttpHeaders.CACHE_CONTROL, "public, max-age=30, must-revalidate")
                    .build();
        } catch (Exception invalidOrUnavailable) {
            return unavailable();
        }
    }

    static void validateJwks(byte[] document, String activeKeyId, RSAPublicKey activePublicKey) throws IOException {
        if (activeKeyId == null || activeKeyId.isBlank() || activeKeyId.length() > 128 || activePublicKey == null) {
            throw new IOException("active signing-key configuration is invalid");
        }
        JsonNode root = JSON.readTree(document);
        JsonNode keys = root == null ? null : root.get("keys");
        if (keys == null || !keys.isArray() || keys.isEmpty() || keys.size() > MAX_KEYS) {
            throw new IOException("JWKS must contain between one and 32 keys");
        }

        Set<String> seenKids = new HashSet<>();
        boolean activeKeyFound = false;
        for (JsonNode key : keys) {
            if (!"RSA".equals(key.path("kty").asText())) {
                continue;
            }
            String use = key.path("use").asText("sig");
            String alg = key.path("alg").asText("RS256");
            JsonNode operations = key.get("key_ops");
            if (!"sig".equals(use) || !"RS256".equals(alg)
                    || (operations != null && (!operations.isArray() || !contains(operations, "verify")))) {
                continue;
            }
            for (String privateMember : PRIVATE_JWK_MEMBERS) {
                if (key.has(privateMember)) {
                    throw new IOException("JWKS must not expose private key material");
                }
            }

            String kid = key.path("kid").asText();
            if (kid.isBlank() || kid.length() > 128 || !seenKids.add(kid)) {
                throw new IOException("JWKS signing key identifiers must be unique and bounded");
            }
            RSAPublicKey publicKey = parseRsaJwk(key);
            if (kid.equals(activeKeyId)) {
                if (!activePublicKey.getModulus().equals(publicKey.getModulus())
                        || !activePublicKey.getPublicExponent().equals(publicKey.getPublicExponent())) {
                    throw new IOException("active JWKS key does not match the configured Identity verification key");
                }
                activeKeyFound = true;
            }
        }
        if (!activeKeyFound) {
            throw new IOException("active signing key is missing from the published JWKS");
        }
    }

    private static RSAPublicKey parseRsaJwk(JsonNode key) throws IOException {
        try {
            byte[] modulusBytes = Base64.getUrlDecoder().decode(key.path("n").asText());
            byte[] exponentBytes = Base64.getUrlDecoder().decode(key.path("e").asText());
            if (modulusBytes.length < 256 || modulusBytes.length > 1024
                    || exponentBytes.length == 0 || exponentBytes.length > 4) {
                throw new IOException("JWKS RSA key size is outside supported bounds");
            }
            BigInteger modulus = new BigInteger(1, modulusBytes);
            BigInteger exponent = new BigInteger(1, exponentBytes);
            if (modulus.bitLength() < 2048 || modulus.bitLength() > 8192
                    || exponent.compareTo(BigInteger.valueOf(3)) < 0
                    || !exponent.testBit(0) || exponent.bitLength() > 31) {
                throw new IOException("JWKS RSA parameters are invalid");
            }
            return (RSAPublicKey) KeyFactory.getInstance("RSA")
                    .generatePublic(new RSAPublicKeySpec(modulus, exponent));
        } catch (IllegalArgumentException | java.security.GeneralSecurityException invalid) {
            throw new IOException("JWKS contains an invalid RSA public key", invalid);
        }
    }

    private static RSAPublicKey readActivePublicKey(FilePath path) throws Exception {
        String pem = Files.readString(path);
        String encoded = pem.replace("-----BEGIN PUBLIC KEY-----", "")
                .replace("-----END PUBLIC KEY-----", "").replaceAll("\\s", "");
        byte[] der = Base64.getDecoder().decode(encoded);
        return (RSAPublicKey) KeyFactory.getInstance("RSA")
                .generatePublic(new X509EncodedKeySpec(der));
    }

    private static boolean contains(JsonNode array, String expected) {
        for (JsonNode value : array) {
            if (expected.equals(value.asText())) {
                return true;
            }
        }
        return false;
    }

    private static Response unavailable() {
        return Response.status(Response.Status.SERVICE_UNAVAILABLE)
                .header(HttpHeaders.CACHE_CONTROL, "no-store").build();
    }
}
