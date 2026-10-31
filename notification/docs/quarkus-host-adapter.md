# Quarkus host adapter

The `notification-adapter-quarkus` module exposes authenticated push-device management endpoints while keeping the notification application and domain layers transport-neutral.

## Endpoints

| Method | Path | Purpose |
|---|---|---|
| POST | `/api/v1/notifications/devices` | Register or refresh a device |
| GET | `/api/v1/notifications/devices` | List active devices for the authenticated recipient |
| DELETE | `/api/v1/notifications/devices/{deviceId}` | Revoke an owned device |

The request body accepts only `token`, `platform`, and `appInstance`. It has no tenant or recipient fields. Responses contain device metadata only. The provider token (including serialized Web Push subscription), ciphertext, key identifier, tenant ID, and recipient ID are never returned.

Unauthenticated requests are rejected by Quarkus `@Authenticated`; a missing verified notification actor is mapped to HTTP 401. A device outside the authenticated recipient scope is returned as HTTP 404.

## Required host bindings

A host must provide CDI implementations for:

- `VerifiedNotificationPrincipalProvider`: reads the verified request context only. In Numou Quarkus hosts, this should bridge `security-adapter/CurrentSecurity.require()` after `RequestSecurityFilter` has run, returning the verified tenant ID and principal ID.
- `NotificationRecipientResolver`: resolves the principal to the notification recipient using an explicit, tenant-scoped identity association. Never assume principal UUID equals recipient UUID.
- `AuthenticatedNotificationApi`: compose the existing notification services and repositories using the host's configured JDBC datasource, token protector/key provider, and clock.

The adapter deliberately does not depend on a particular identity-to-recipient schema or silently equate IDs. This allows a host to use user, service, Party, or participant identity mappings without leaking those decisions into the notification capability.

## Production controls

- Protect all endpoints with the host's verified authentication and tenant-resolution filter; configure Quarkus HTTP permissions as well as the resource-level `@Authenticated` guard.
- Use the encrypted JDBC token persistence and managed key provider; never use test keys in production.
- For Web Push, use host-managed VAPID credentials and an HTTP client with DNS/IP-aware egress controls, connection pinning, or a trusted egress proxy. HTTPS URL validation alone does not prevent SSRF or DNS rebinding.
- Configure rate limits and request-body limits at the host gateway. The adapter caps token input at the existing 4 KiB domain contract and app-instance labels at 200 characters.
- Keep provider selection explicit at registration/enqueue time. Do not infer a provider from token shape or silently fall back between FCM, APNs, and Web Push.

## Build

Run `mvn -B -ntp -f notification/pom.xml verify` from the repository root after installing the framework parent POM as documented in the Notification capability workflow.
