package tech.kayys.syirkah.observability;

import java.util.Objects;
import java.util.UUID;

/**
 * The common telemetry context (base01.md §P1-19).
 *
 * <p>Carried through every important operation so the whole journey can be
 * reconstructed:
 *
 * <pre>
 *   TRACE-001
 *   |-- API request
 *   |-- RegisterOrganization
 *   |-- DB transaction
 *   |-- OrganizationRegistered
 *   |-- Outbox publish
 *   |-- Audit
 *   |-- External webhook
 *   </pre>
 *
 * <p>Immutable by design: a context is created once at the edge of a
 * request and derived thereafter, never mutated in place.
 *
 * @param traceId       distributed tracing id
 * @param spanId        the current span
 * @param correlationId whole business journey
 * @param causationId   the step that produced this one
 * @param tenantId      tenant isolation boundary
 * @param participantId ecosystem participant
 * @param userId        acting user or service account
 * @param requestId     transport-level request identity
 */
public record TelemetryContext(
        String traceId,
        String spanId,
        String correlationId,
        String causationId,
        UUID tenantId,
        UUID participantId,
        String userId,
        String requestId) {

    /**
     * Starts a fresh context for a new request, generating the ids that
     * do not arrive from outside.
     */
    public static TelemetryContext start(UUID tenantId, String userId) {
        final var correlationId = UUID.randomUUID().toString();
        return new TelemetryContext(
                UUID.randomUUID().toString(),
                UUID.randomUUID().toString(),
                correlationId,
                null,
                tenantId,
                null,
                userId,
                correlationId);
    }

    /** Derives a child context for a nested step (event, webhook, ...). */
    public TelemetryContext child(String newSpanId) {
        Objects.requireNonNull(newSpanId, "newSpanId cannot be null");
        return new TelemetryContext(
                traceId,
                newSpanId,
                correlationId,
                causationId,
                tenantId,
                participantId,
                userId,
                requestId);
    }

    /** Returns a copy attributed to the given acting user. */
    public TelemetryContext withUser(String actingUserId) {
        return new TelemetryContext(
                traceId, spanId, correlationId, causationId,
                tenantId, participantId, actingUserId, requestId);
    }

    public TelemetryContext withParticipant(UUID actingParticipantId) {
        return new TelemetryContext(
                traceId, spanId, correlationId, causationId,
                tenantId, actingParticipantId, userId, requestId);
    }

    /** True when this context is scoped to a single tenant. */
    public boolean isTenantScoped() {
        return tenantId != null;
    }
}
