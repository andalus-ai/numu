package tech.kayys.numou.identity.application.dto;

import java.util.List;
import java.util.Objects;

/** SCIM PATCH operation (RFC 7644 §3.5.2) - replace-everything or explicit op list. */
public record ScimPatchOperation(
        String op,
        String path,
        Object value
) {

    public ScimPatchOperation {
        Objects.requireNonNull(op, "op cannot be null");
    }
}
