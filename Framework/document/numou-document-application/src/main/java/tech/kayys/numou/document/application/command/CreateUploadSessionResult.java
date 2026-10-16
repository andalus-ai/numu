package tech.kayys.numou.document.application.command;

import tech.kayys.numou.accounting.domain.document.UploadSessionId;

import java.time.Instant;
import java.util.Map;

public record CreateUploadSessionResult(
        UploadSessionId uploadSessionId,
        String uploadUrl,
        Instant expiresAt,
        Map<String, String> requiredHeaders
) {
    public CreateUploadSessionResult {
        requiredHeaders = requiredHeaders == null ? Map.of() : Map.copyOf(requiredHeaders);
    }

    public CreateUploadSessionResult(UploadSessionId uploadSessionId, String uploadUrl, Instant expiresAt) {
        this(uploadSessionId, uploadUrl, expiresAt, Map.of());
    }
}
