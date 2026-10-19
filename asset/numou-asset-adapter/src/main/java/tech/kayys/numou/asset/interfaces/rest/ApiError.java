package tech.kayys.numou.asset.interfaces.rest;

import java.time.Instant;

/**
 * Standardized API error body (ASSET-28 §error semantics, §42).
 *
 * <p>Every Asset endpoint returns this shape on failure: a stable machine
 * {@code code}, a human message, the HTTP {@code status} and observability
 * context ({@code correlationId}, {@code occurredAt}).</p>
 */
public record ApiError(
        String code,
        String message,
        int status,
        String correlationId,
        Instant occurredAt
) {

    public static ApiError of(String code, String message, int status) {
        return new ApiError(code, message, status, null, Instant.now());
    }
}