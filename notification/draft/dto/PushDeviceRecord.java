package id.mindo.notification.dto;

import java.time.OffsetDateTime;

public class PushDeviceRecord {
    private String clientId;
    private String token;
    private String platform;
    private String appInstance;
    private OffsetDateTime updatedAt;

    public PushDeviceRecord() {
    }

    public PushDeviceRecord(
            String clientId,
            String token,
            String platform,
            String appInstance,
            OffsetDateTime updatedAt) {
        this.clientId = clientId;
        this.token = token;
        this.platform = platform;
        this.appInstance = appInstance;
        this.updatedAt = updatedAt;
    }

    public String getClientId() {
        return clientId;
    }

    public void setClientId(String clientId) {
        this.clientId = clientId;
    }

    public String getToken() {
        return token;
    }

    public void setToken(String token) {
        this.token = token;
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
}
