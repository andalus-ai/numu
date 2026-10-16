package tech.kayys.numou.foundation.adapter.rest;

import java.time.Instant;

/**
 * Standard RFC-7807 compliant error payload for ERP REST APIs.
 */
public record ErrorResponse(
        String code,
        String message,
        int status,
        Instant timestamp
) {
    public ErrorResponse(String code, String message, int status) {
        this(code, message, status, Instant.now());
    }
}
