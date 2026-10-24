# Schedule-change outbox operations

The planning PostgreSQL adapter exposes a restricted read-only operational endpoint:

`GET /internal/operations/planning/outbox`

The endpoint is protected with Jakarta `@RolesAllowed("platform-operations")`. The host application must configure an authentication mechanism and map its trusted operator identity to the `platform-operations` role. Do not expose this route through an unauthenticated public ingress. Prefer the host's internal management listener or an authenticated administrative gateway.

The response contains only aggregate operational signals:

- `pendingCount`: unpublished, not currently leased events without a prior delivery error.
- `leasedCount`: unpublished events currently claimed by a relay worker.
- `retryingCount`: unpublished events that failed at least once and are not currently leased.
- `deadLetterCount`: events that exhausted the ten-attempt delivery policy.
- `oldestPendingAgeSeconds`: age of the oldest unpublished, non-dead-lettered event.

The endpoint deliberately omits event payloads, tenant identifiers, and persisted error messages. Treat counts and age as monitoring signals, not as a delivery guarantee. Relay delivery is at-least-once; consumers must deduplicate using the stable outbox ID.

This endpoint does not configure authentication itself. Integrators must configure the application's supported identity provider or HTTP authentication mechanism and verify that requests without the required role are rejected before enabling the route in production.
