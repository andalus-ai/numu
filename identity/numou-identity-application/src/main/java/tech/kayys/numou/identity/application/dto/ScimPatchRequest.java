package tech.kayys.numou.identity.application.dto;

import java.util.List;
import java.util.Objects;

/** SCIM PATCH request body (RFC 7644 §3.5.2): an explicit operation list. */
public record ScimPatchRequest(
        List<String> schemas,
        List<ScimPatchOperation> operations
) {

    public ScimPatchRequest {
        schemas = schemas == null ? List.of() : List.copyOf(schemas);
        operations = operations == null ? List.of() : List.copyOf(operations);
    }
}
