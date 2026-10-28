package id.mindo.notification.dto;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.OffsetDateTime;
import java.util.HexFormat;

public class PushDeviceSummary {

    private String clientId;
    private String tokenSuffix;
    private String tokenFingerprint;
    private String platform;
    private String appInstance;
    private OffsetDateTime updatedAt;

    public PushDeviceSummary() {
    }

    public PushDeviceSummary(
            String clientId,
            String tokenSuffix,
            String tokenFingerprint,
            String platform,
            String appInstance,
            OffsetDateTime updatedAt) {
        this.clientId = clientId;
        this.tokenSuffix = tokenSuffix;
        this.tokenFingerprint = tokenFingerprint;
        this.platform = platform;
        this.appInstance = appInstance;
        this.updatedAt = updatedAt;
    }

    public static PushDeviceSummary fromRecord(PushDeviceRecord record) {
        final String token = normalize(record == null ? null : record.getToken());
        return new PushDeviceSummary(
                normalize(record == null ? null : record.getClientId()),
                tokenSuffix(token),
                tokenFingerprint(token),
                normalize(record == null ? null : record.getPlatform()),
                normalize(record == null ? null : record.getAppInstance()),
                record == null ? null : record.getUpdatedAt());
    }

    public String getClientId() {
        return clientId;
    }

    public void setClientId(String clientId) {
        this.clientId = clientId;
    }

    public String getTokenSuffix() {
        return tokenSuffix;
    }

    public void setTokenSuffix(String tokenSuffix) {
        this.tokenSuffix = tokenSuffix;
    }

    public String getTokenFingerprint() {
        return tokenFingerprint;
    }

    public void setTokenFingerprint(String tokenFingerprint) {
        this.tokenFingerprint = tokenFingerprint;
    }

    public String getPlatform() {
        return platform;
    }

    public void setPlatform(String platform) {
        this.platform = platform;
    }

    public String getAppInstance() {
        return appInstance;
    }

    public void setAppInstance(String appInstance) {
        this.appInstance = appInstance;
    }

    public OffsetDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(OffsetDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }

    private static String tokenSuffix(String token) {
        if (token.isEmpty()) {
            return "";
        }
        final int start = Math.max(0, token.length() - 8);
        return token.substring(start);
    }

    private static String tokenFingerprint(String token) {
        if (token.isEmpty()) {
            return "";
        }
        try {
            final MessageDigest digest = MessageDigest.getInstance("SHA-256");
            final byte[] hash = digest.digest(token.getBytes(StandardCharsets.UTF_8));
            return "sha256:" + HexFormat.of().formatHex(hash).substring(0, 16);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is not available", e);
        }
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim();
    }
}
