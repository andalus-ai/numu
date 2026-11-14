# Social Graph M2g — Notification delivery suppression

## Ownership

Notification owns durable inbox entries, delivery jobs, provider retries and
provider state. Social Graph owns relationship semantics. The
`notification-adapter-social-graph` module composes the two public
application contracts; neither capability reads or writes the other's tables.

## Implementation

- `Notification.sourceActorPartyId` stores optional canonical Ecosystem
  Party attribution for social-originated notification intent. Existing
  system-originated producers remain source-compatible through the old
  constructor/factory overload.
- Notification creation persists this field and includes it in the
  idempotency fingerprint. Reusing the same idempotency key with a different
  source Party is rejected.
- `NotificationDeliveryPolicy` is a required worker dependency. It is
  evaluated after the notification is loaded and before device lookup/provider
  dispatch.
- The adapter resolves the recipient's canonical Party through a host-owned,
  tenant-scoped `NotificationRecipientPartyResolver`. It does not
  infer Party identity from UUID equality.
- The recipient is evaluated as viewer and the source Party as author using
  `NOTIFICATION_DELIVERY` semantics from Social Graph. System-originated
  notifications without a source Party skip relationship evaluation; producers
  must attach canonical source attribution for Party-authored content/actions.
- `BLOCKED`, `MUTED` and `UNRESOLVED` produce
  terminal `SUPPRESSED` delivery jobs with safe reason codes.
- Policy infrastructure failures are treated as retryable internal failures;
  the provider is never called while the policy result is unknown.
- Suppression does not delete or mutate inbox notification content, source
  events, audit history or upstream outbox intent.

## Migration

V6 adds nullable `source_actor_party_id` to `notification_inbox`
and adds `SUPPRESSED` to the delivery-job state constraint. Existing
rows remain valid and are treated as system-originated unless a trusted
producer begins supplying source attribution.

## Host responsibilities

- Include `numou-notification-adapter-social-graph` only in compositions
  that enable Social Graph-aware delivery.
- Supply an implementation of `NotificationRecipientPartyResolver`
  backed by verified tenant-scoped identity associations.
- Populate `sourceActorPartyId` from trusted domain context, not from
  untrusted request fields.
- Pass the adapter as the worker's required `NotificationDeliveryPolicy`.
- Run the synchronous worker on a blocking scheduler thread because the adapter
  waits for the Mutiny Social Graph policy result.
- Keep profile visibility, consent, moderation, recipient eligibility and
  channel-specific policy checks in their owning capabilities. Social Graph
  suppression is one delivery gate, not a replacement for those checks.

## Verification target

The PR adds adapter directionality/unresolved-identity tests, notification
attribution persistence tests, worker policy injection, and PostgreSQL coverage
for terminal suppression. Maven and Testcontainers CI must pass before this
milestone is considered verified.
