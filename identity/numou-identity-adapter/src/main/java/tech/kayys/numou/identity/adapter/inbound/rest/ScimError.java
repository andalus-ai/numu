package tech.kayys.numou.identity.adapter.inbound.rest;

/**
 * SCIM 2.0 error response body (RFC 7644 §3.5).
 *
 * @param schemas    SCIM error schema URN
 * @param status     HTTP status code as a string
 * @param scimType   SCIM-specific error keyword (e.g. {@code uniqueness}, {@code invalidValue})
 * @param detail     Human-readable error description
 */
public record ScimError(
        java.util.List<String> schemas,
        String status,
        String scimType,
        String detail
) {

    public ScimError {
        schemas = schemas == null
                ? java.util.List.of("urn:ietf:params:scim:api:messages:2.0:Error")
                : java.util.List.copyOf(schemas);
    }

    public static ScimError of(int status, String scimType, String detail) {
        return new ScimError(null, String.valueOf(status), scimType, detail);
    }
}
