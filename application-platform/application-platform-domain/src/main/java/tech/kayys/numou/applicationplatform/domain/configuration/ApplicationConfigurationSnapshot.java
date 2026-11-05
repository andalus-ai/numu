package tech.kayys.numou.applicationplatform.domain.configuration;

import tech.kayys.numou.applicationplatform.domain.installation.ApplicationId;
import tech.kayys.numou.foundation.domain.tenant.TenantId;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * Immutable, tenant-owned configuration revision for one installed application.
 *
 * <p>The payload is validated by the application-layer schema port before a snapshot
 * is persisted. Secret material must never be included in payloadJson; integrations
 * persist opaque secret references and resolve values only at runtime.</p>
 */
public record ApplicationConfigurationSnapshot(
        TenantId tenantId,
        ApplicationId applicationId,
        String schemaVersion,
        long revision,
        String payloadJson,
        String payloadSha256,
        String updatedBy,
        Instant updatedAt) {

    private static final Pattern SCHEMA_VERSION = Pattern.compile("^[0-9]+\\.[0-9]+(?:\\.[0-9]+)?$");
    private static final Pattern SHA256 = Pattern.compile("^[a-f0-9]{64}$");

    public ApplicationConfigurationSnapshot {
        Objects.requireNonNull(tenantId, "tenantId");
        Objects.requireNonNull(applicationId, "applicationId");
        if (schemaVersion == null || !SCHEMA_VERSION.matcher(schemaVersion).matches())
            throw new IllegalArgumentException("schemaVersion must be a numeric major.minor[.patch] version");
        if (revision < 1) throw new IllegalArgumentException("revision must be positive");
        if (payloadJson == null || payloadJson.isBlank() || payloadJson.length() > 1_048_576)
            throw new IllegalArgumentException("payloadJson must contain 1..1048576 characters");
        String expectedDigest = sha256(payloadJson);
        if (payloadSha256 == null || !SHA256.matcher(payloadSha256).matches()
                || !expectedDigest.equals(payloadSha256))
            throw new IllegalArgumentException("payloadSha256 does not match payloadJson");
        if (updatedBy == null || updatedBy.isBlank() || updatedBy.length() > 200)
            throw new IllegalArgumentException("updatedBy must contain 1..200 characters");
        Objects.requireNonNull(updatedAt, "updatedAt");
    }

    public static ApplicationConfigurationSnapshot first(TenantId tenantId, ApplicationId applicationId,
            String schemaVersion, String payloadJson, String actorId, Instant now) {
        return new ApplicationConfigurationSnapshot(tenantId, applicationId, schemaVersion, 1,
                payloadJson, sha256(payloadJson), actorId, now);
    }

    public ApplicationConfigurationSnapshot next(String nextSchemaVersion, String nextPayloadJson,
            String actorId, Instant now) {
        return new ApplicationConfigurationSnapshot(tenantId, applicationId, nextSchemaVersion,
                Math.addExact(revision, 1), nextPayloadJson, sha256(nextPayloadJson), actorId, now);
    }

    private static String sha256(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 is required by the Java runtime", impossible);
        }
    }
}
