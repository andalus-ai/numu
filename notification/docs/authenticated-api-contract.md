# Authenticated Notification API Contract

## Boundary

```text
HTTP adapter -> AuthenticatedNotificationApi -> NotificationService / PushDeviceService
                                            -> NotificationRequestContext (host port)
```

`AuthenticatedNotificationApi` is the actor-scoped application facade. It intentionally accepts no tenant or recipient IDs on inbox, read-state, or device-management operations. It resolves scope for each operation from `NotificationRequestContext`.

## Host integration requirements

A host implementation of `NotificationRequestContext` must:

1. Read only a verified `tech.kayys.numou.security.application.context.SecurityContext` (or an equivalent trusted identity context).
2. Resolve the tenant from the verified context after Numou tenant-membership checks.
3. Map the verified principal to the notification recipient ID using an explicit identity mapping. Do not assume principal UUID and recipient UUID are always the same.
4. Return `Optional.empty()` when no authenticated actor exists.
5. Never use tenant IDs, recipient IDs, usernames, or emails from untrusted request fields as authorization.

REST adapters should map `UnauthenticatedNotificationRequestException` to HTTP 401 and an owned-resource miss to HTTP 404 (or the host's documented non-disclosing equivalent). Request validation failures should map to HTTP 400. Provider tokens must never appear in response DTOs or logs.

## Application operations

- `inbox(limit, beforeCreatedAt)`: recipient-scoped inbox with service-enforced limit cap.
- `markRead(notificationId)`: recipient-scoped, idempotent read transition.
- `registerDevice(token, platform, appInstance)`: register/refresh for the verified actor; response contains metadata only.
- `activeDevices()`: list active metadata-only device views for the verified actor.
- `unregisterDevice(deviceId)`: disable only within the verified actor scope; response contains metadata only.

The `DeviceView` contract includes device ID, platform, app-instance label, lifecycle timestamps, and active state. It deliberately excludes tenant/recipient identifiers and all provider credential material. Registration input contains no tenant or recipient fields.

## Push subscription lifecycle

For browser clients, the host adapter should accept the browser Push API subscription serialized as the opaque `token` value with platform `WEB`. The application/domain boundary enforces the existing 4 KiB token limit. The Web Push provider parses and validates endpoint, P-256 public key, and authentication secret before delivery. The full subscription must remain protected by `PushTokenProtector` at rest.

Registration/refresh is idempotent for the existing tenant-recipient-token fingerprint and does not create a second device row. Listing and revocation are scoped to the verified actor and use the metadata-only repository projections introduced in N12. A provider's permanent expired-subscription response (404/410) must be handled through the delivery lifecycle; it must not be translated into a client-visible provider token.

## Deployment boundary and remaining host work

This is an application contract, not a deployable REST endpoint. A host-specific REST adapter and CDI composition must supply the verified request-context implementation and configured service beans. The repository's legacy `notification/draft` resource is not the production adapter for this contract and must not be treated as such.

Provider-token encryption at rest and key rotation remain production requirements before push delivery is enabled. Web Push additionally requires host-managed VAPID credentials and a DNS/IP-aware egress policy (or trusted egress proxy) that prevents SSRF and DNS-rebinding attacks. The provider intentionally accepts a host-configured HTTP client rather than claiming that endpoint URL validation alone is sufficient.
