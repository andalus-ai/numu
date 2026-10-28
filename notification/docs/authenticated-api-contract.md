# Authenticated Notification API Contract (N3)

## Boundary

```text
HTTP adapter -> AuthenticatedNotificationApi -> NotificationService / PushDeviceService
                                            -> NotificationRequestContext (host port)
```

AuthenticatedNotificationApi is the actor-scoped application facade. It intentionally accepts no tenant or recipient IDs on inbox, read-state, or device-management operations. It resolves scope for each operation from NotificationRequestContext.

## Host integration requirements

A host implementation of NotificationRequestContext must:

1. Read only a verified tech.kayys.numou.security.application.context.SecurityContext (or an equivalent trusted identity context).
2. Resolve the tenant from the verified context after Numou tenant-membership checks.
3. Map the verified principal to the notification recipient ID using an explicit identity mapping. Do not assume principal UUID and recipient UUID are always the same.
4. Return Optional.empty() when no authenticated actor exists.
5. Never use tenant IDs, recipient IDs, usernames, or emails from untrusted request fields as authorization.

REST adapters should map UnauthenticatedNotificationRequestException to HTTP 401 and an owned-resource miss to HTTP 404 (or the host's documented non-disclosing equivalent). Request validation failures should map to HTTP 400. Provider tokens must never appear in response DTOs or logs.

## Operations exposed by the application facade

- inbox(limit, beforeCreatedAt): recipient-scoped inbox with service-enforced limit cap.
- markRead(notificationId): recipient-scoped, idempotent read transition.
- registerDevice(token, platform, appInstance): register/refresh for the verified actor.
- activeDevices(): list metadata only; no provider token.
- unregisterDevice(deviceId): disable only within the verified actor scope.

This is an application contract, not yet a deployable REST endpoint. A host-specific REST adapter and CDI composition must supply the verified request-context implementation and configured service beans. Provider-token encryption at rest and key rotation remain production requirements before push delivery is enabled.
