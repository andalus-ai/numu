package tech.kayys.numou.identity.adapter.inbound.rest;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.math.BigInteger;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPublicKey;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class OAuthJWKSResourceTest {

    @Test
    void acceptsActiveAndPreviousSigningKeysDuringRotationOverlap() throws Exception {
        RSAPublicKey active = generatePublicKey();
        RSAPublicKey previous = generatePublicKey();
        byte[] document = jwks(
                jwk("key-2026-11", active),
                jwk("key-2026-10", previous));

        assertDoesNotThrow(() -> OAuthJWKSResource.validateJwks(document, "key-2026-11", active));
    }

    @Test
    void rejectsJwksWithoutTheConfiguredActiveKey() throws Exception {
        RSAPublicKey active = generatePublicKey();
        RSAPublicKey other = generatePublicKey();

        assertThrows(IOException.class, () -> OAuthJWKSResource.validateJwks(
                jwks(jwk("not-active", other)), "key-active", active));
    }

    @Test
    void rejectsAnActiveKeyThatDoesNotMatchTheConfiguredVerifierKey() throws Exception {
        RSAPublicKey active = generatePublicKey();
        RSAPublicKey substituted = generatePublicKey();

        assertThrows(IOException.class, () -> OAuthJWKSResource.validateJwks(
                jwks(jwk("key-active", substituted)), "key-active", active));
    }

    @Test
    void rejectsDuplicateKeyIdsAndPrivateMaterial() throws Exception {
        RSAPublicKey active = generatePublicKey();
        String activeJwk = jwk("key-active", active);

        assertThrows(IOException.class, () -> OAuthJWKSResource.validateJwks(
                jwks(activeJwk, activeJwk), "key-active", active));

        String privateKeyJwk = activeJwk.substring(0, activeJwk.length() - 1) + ",\"d\":\"must-not-be-published\"}";
        assertThrows(IOException.class, () -> OAuthJWKSResource.validateJwks(
                jwks(privateKeyJwk), "key-active", active));
    }

    @Test
    void rejectsWeakRsaMaterialAndOversizedKeySets() throws Exception {
        RSAPublicKey active = generatePublicKey();
        String weak = "{\"kty\":\"RSA\",\"use\":\"sig\",\"alg\":\"RS256\","
                + "\"kid\":\"key-active\",\"n\":\"AQ\",\"e\":\"Aw\"}";
        assertThrows(IOException.class, () -> OAuthJWKSResource.validateJwks(
                jwks(weak), "key-active", active));

        StringBuilder many = new StringBuilder("{\"keys\":[");
        for (int index = 0; index < 33; index++) {
            if (index > 0) many.append(',');
            many.append(jwk("key-" + index, active));
        }
        many.append("]}");
        assertThrows(IOException.class, () -> OAuthJWKSResource.validateJwks(
                many.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8), "key-active", active));
    }

    private static RSAPublicKey generatePublicKey() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        KeyPair pair = generator.generateKeyPair();
        return (RSAPublicKey) pair.getPublic();
    }

    private static String jwk(String kid, RSAPublicKey key) {
        byte[] modulus = unsigned(key.getModulus());
        byte[] exponent = unsigned(key.getPublicExponent());
        return "{\"kty\":\"RSA\",\"use\":\"sig\",\"alg\":\"RS256\","
                + "\"kid\":\"" + kid + "\","
                + "\"n\":\"" + Base64.getUrlEncoder().withoutPadding().encodeToString(modulus) + "\","
                + "\"e\":\"" + Base64.getUrlEncoder().withoutPadding().encodeToString(exponent) + "\"}";
    }

    private static byte[] unsigned(BigInteger value) {
        byte[] bytes = value.toByteArray();
        if (bytes.length > 1 && bytes[0] == 0) {
            return java.util.Arrays.copyOfRange(bytes, 1, bytes.length);
        }
        return bytes;
    }

    private static byte[] jwks(String... keys) {
        String document = "{\"keys\":[" + String.join(",", keys) + "]}";
        return document.getBytes(java.nio.charset.StandardCharsets.UTF_8);
    }
}
