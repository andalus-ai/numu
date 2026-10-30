# APNs provider

The HTTP provider module exposes an APNs adapter using the shared
`NotificationDeliveryProvider` port. The host composes it into the multi-provider
worker with provider code `apns`.

## Credential boundary

`ApnsTenantCredentialResolver` is host-owned. For each delivery it resolves:
- the APNs topic (bundle identifier),
- a short-lived APNs provider JWT,
- the JWT expiry.

The host MUST authorize the tenant against the configured topic before returning
credentials. The notification module does not store APNs signing keys, generate
JWTs, or persist provider credentials. A production host should mint/cache JWTs
using a managed secret/KMS-backed signer and rotate credentials without restarting
the notification worker.

Use the APNs production endpoint by default. The sandbox endpoint is a separate
host configuration for development/test applications. Never choose the APNs
environment based on untrusted request data.

## Delivery and invalidation

The adapter sends alert payloads with `apns-push-type: alert`, the tenant-resolved
`apns-topic`, and bounded request timeouts. It maps explicit unregistered-token
responses (including HTTP 410) to `provider_device_unregistered`; transient
throttling and server failures are retryable. Provider response bodies and
credential material are not exposed through delivery error codes.

## Web Push boundary (next milestone)

Web Push is intentionally not implemented by treating the existing opaque device
token as a URL. A standards-compliant subscription needs an endpoint, a
P-256 ECDH public key, and an authentication secret; payload delivery also needs
RFC 8291 encryption and VAPID authorization. The current `PushDevice` model has
only one opaque token field and a 4 KiB limit. The next milestone should introduce
an explicit subscription value object and protected persistence representation,
then add encryption, VAPID signing/rotation, endpoint allowlisting/SSRF controls,
and lifecycle tests. Until then, do not enqueue `webpush` jobs.
