package tech.kayys.syirkah.accounting.domain.document;

import tech.kayys.syirkah.foundation.domain.identifier.DomainId;

import java.util.Objects;
import java.util.UUID;

public record UploadSessionId(String value) implements DomainId<String> {
    public UploadSessionId {
        Objects.requireNonNull(value, "value cannot be null");
        if (value.isBlank()) {
            throw new IllegalArgumentException("UploadSessionId cannot be blank");
        }
    }

    public static UploadSessionId generate() {
        return new UploadSessionId(UUID.randomUUID().toString());
    }
}
