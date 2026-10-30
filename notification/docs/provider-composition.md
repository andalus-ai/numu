# Notification provider composition

The notification worker supports one or more provider adapters. The provider code
persisted on each delivery job is the dispatch key:

- A single-provider deployment can keep using the single-provider constructor.
- A multi-provider deployment supplies a map keyed by each adapter's exact
  `providerCode()` value.
- The worker resolves the provider from the job's stored provider code before
  calling the adapter. It never falls back to a different provider when the
  configured adapter is missing; the job is marked dead with
  `provider_unavailable`.

This preserves provider affinity for already-enqueued jobs during rolling
deployments and provider migrations. Configure only adapters whose credentials,
tenant authorization, timeout, quota, and token lifecycle integrations are ready.

The provider map is immutable after worker construction. Hosts should build it
from deployment configuration and keep secrets in their managed credential
services. Do not put provider credentials in the map key, job payload, or logs.

The current device registration model distinguishes Android, iOS, and Web
platforms. Provider selection remains an explicit job-level decision; the
application does not silently infer a provider from a token's shape. This allows
a host to choose FCM, APNs, or Web Push according to its tenant/application
configuration and migration policy.
