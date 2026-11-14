# Notification Social Graph adapter

This adapter composes Notification's provider-neutral delivery policy port with
Social Graph's authoritative relationship policy. It is optional: hosts that
want Social Graph-aware notification delivery include this artifact and pass
its `SocialGraphNotificationDeliveryPolicyAdapter` to the
`NotificationDeliveryWorker`.

## Semantics

- Recipient Party is the viewer/actor; source Party is the notification author.
- Symmetric BLOCK suppresses delivery regardless of which endpoint created the
  directed block edge.
- Recipient-owned MUTE suppresses notification delivery only.
- A MUTE does not deny messages, comments, invitations, or other direct actions.
- System-originated notifications with no source Party do not invent an author
  and are allowed by this relationship policy.
- If a notification has a source Party but the recipient cannot be resolved to
  an active tenant-scoped canonical Party, the adapter returns `UNRESOLVED`.
  The worker records a terminal suppression; it never falls back to UUID equality.
- A policy-store outage is retried by the worker and the provider is not called.

## Trusted identity mapping

The host must implement `NotificationRecipientPartyResolver` using a
verified, tenant-scoped identity-to-Ecosystem Party association. The adapter
does not assume Notification's `recipientId` is equal to `PartyId`.
A source producer may set `sourceActorPartyId` only when it holds the
canonical Ecosystem Party ID from trusted domain context. Do not populate it
from free-form payload metadata, a source-reference string, or a caller-supplied
unverified UUID.

## Durable suppression

Policy suppression is persisted as `SUPPRESSED`, separate from provider
`DEAD`. The delivery job retains a safe reason code such as
`social_graph_blocked`, `social_graph_muted`, or
`social_graph_party_unresolved`. The notification inbox record and
business source event remain unchanged. Suppressed jobs are terminal and are
not selected by the provider retry query.

The host must construct the worker with a non-null `NotificationDeliveryPolicy`
implementation. If Social Graph integration is enabled, use this adapter rather
than a permissive fallback. Run the worker on a blocking worker/scheduler
thread: the Social Graph application contract is Mutiny-based and this adapter
waits for its authoritative result before provider dispatch.
