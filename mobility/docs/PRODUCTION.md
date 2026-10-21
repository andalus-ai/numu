# Numou – production hardening + restaurant suite + ride-hailing suite

> **Update:** a complete ride-hailing suite (`mobility/`) was added on top of this platform, and the generic aggregate kernel/persistence was
> extracted into `aggregate/` (the restaurant modules now depend on it; table `restaurant_document` is now `aggregate_document`).
> Read **`MOBILITY.md`** for the design and operations, and **`GAPS.md`** for an honest list of what is still missing before a real launch.
> Whole-repo status: **415 tests, 0 failures**; **about 60 framework files remain uncompiled** (see §1).



This overlay adds production capabilities (security, tenancy, caching, observability, reliability, deployment) and a
restaurant domain (venue/menu/tables, dine-in orders, kitchen, reservations/waitlist). It follows your existing
hexagonal layout (`domain / spi / application / adapter`) and conventions (record IDs, `AbstractAggregateRoot`, `raise()`,
Mutiny `Uni`, `UnitOfWork`).

## 1. Read this first: what is and is not verified

The authoring environment had a JDK, JUnit, AssertJ, Jackson, Caffeine and PostgreSQL 16, but **no Maven Central**, so
Quarkus, Hibernate Reactive, Vert.x, Micrometer and OpenTelemetry could not be resolved. Consequences:

| Area | How it was checked | Status |
|---|---|---|
| Domain + application logic of every new module, cache, security core, observability decorators, reliability core, JSON event wire format, event decoder | Compiled with `javac` against **your real foundation/event/security/reliability sources**, **415 JUnit tests, 0 failures** (Mutiny replaced by a small synchronous stand-in used only for tests) | Verified |
| Concurrency (last-table race, duplicate course fire, cache single-flight / invalidation race) | Real threads: 15 rounds × 8 threads, 10 rounds × 6 threads; cache tests run repeatedly | Verified |
| SQL (outbox claim `SKIP LOCKED`, lease expiry, dead letters, inbox, idempotency claim race, append-only audit, row-level security, optimistic locking, PII retention) | Executed on PostgreSQL 16 **as a non-superuser role** (superusers bypass RLS); V100→V200→V201 applied in order to a clean DB | Verified |
| Maven reactor wiring | `tools/check-reactor.py`: unique artifactIds, modules/parents resolve, no cycles, every imported package belongs to a declared dependency, domain layers import no framework | Verified (not by Maven itself) |
| YAML (k8s, compose, Prometheus, OTel, CI) | Parsed | Syntax only |
| **about 58 new Quarkus/Vert.x/Micrometer/OTel/Kafka classes + the 2 replaced context classes (60 files)** | Written carefully, kept thin (logic lives in the tested classes), **never compiled** | **Unverified** |

**The unverified classes**: `restaurant-api` (resources, producer, event consumer, booking-rules producer, problem rules,
`Secured`), `reliability-adapter` (`Pg*Store`, `OutboxRelayScheduler`, `OutboxMetricsBinder`, `KafkaEventPublisher`,
`ReliabilityProducer`), `security-adapter` (filters, mapper, producers), `restaurant-persistence` (`PgDocumentRepository`,
`PgRestaurantRepositories`, `TenantTransaction`, `PgUnitOfWork`, `PersistenceAnchor`), `observability-adapter`
(request filter, Micrometer, OTel), and the foundation-adapter `RequestContextHolder`/`TenantContext`/`CorrelationContext`.
Expect compile-level fixes (imports, API signatures for your exact Quarkus 3.15.1) and plan an integration pass; the
checklist is in §8 and the wider launch checklist in `GAPS.md`.

## 2. How to apply

Copy this tree over the repository root. **Nine existing files are replaced** (all small, additive edits):

* `pom.xml` (+modules `cache`, `restaurant`), `bom/pom.xml` (+27 managed artifacts)
* `security/pom.xml`, `observability/pom.xml`, `reliability/pom.xml` (+ new child modules)
* `foundation/numou-foundation-adapter/pom.xml` (+`quarkus-arc`, provided)
* `foundation/.../context/TenantContext.java`, `CorrelationContext.java` (same static API, now request-scoped; §3.2)
* `reliability/reliability-spi/.../IdempotencyStorePort.java` (+`release(...)`; nothing else in the repo implements it)

New modules: `cache/{cache-api,cache-adapter}`, `security/{security-application,security-adapter}`,
`observability/{observability-application,observability-adapter}`, `reliability/{reliability-application,reliability-adapter}`,
and `restaurant/` (kernel, 4 contexts × 4 layers, integration, persistence, api). Plus `deploy/`, `docs/`, `tools/`, `.github/`.

## 3. Problems found in the existing code (fixed or mitigated here)

1. **Tenant spoofing (critical).** `AssetResource` takes the tenant from the `X-Tenant-Id` header with no authentication
   check: any caller can read/write any tenant. *Mitigation:* `RequestSecurityFilter` derives the tenant from the validated
   token; a header is accepted only if it names a tenant the principal belongs to, a contradicting header is a 403. Existing
   resources become safe without edits because the request never reaches them with a mismatching header. **The asset module
   is not otherwise changed**: also add the filter module to its dependencies and remove its own `ApiExceptionMapper`.
2. **`ThreadLocal` tenant/correlation in a reactive service (critical).** One event-loop thread interleaves requests, so
   request B can overwrite request A's tenant while A awaits I/O, then A reads B's tenant. *Fix:* request-scoped
   `RequestContextHolder`; the static API is unchanged; a verified tenant cannot be switched afterwards.
3. **Outbox payload is `event.toString()`**, truncated at 4,000 chars: not parseable, so events cannot be consumed. *Fix:*
   `JsonEventSerializer` (explicit wire format v1) + `OutboxDomainEventSink`.
4. **Idempotency store races and hides outages.** Check-then-insert (two concurrent requests can both win) and
   `recoverWithItem(FALSE)` treats a database failure as "duplicate". *Fix:* single `INSERT … ON CONFLICT DO NOTHING`
   (8-session race test: exactly one winner), failures propagate, and a failed first attempt now releases its claim.
5. **Error bodies leak internals.** `ApiExceptionMapper` echoes `cause.getMessage()` on 500s (SQL, hostnames).
   *Fix:* `ProblemMapper`: 5xx are generic with a correlation id, 4xx messages are PII-redacted.
6. **Reactor cannot build as supplied:** modules/artifacts `document`, `numou-foundation-testing` (referenced from
   `pom.xml`, `foundation/pom.xml`, many poms) and `billing-core` (finance reporting) do not exist in the dump.
   Not touched; `tools/check-reactor.py` reports them.
7. No authentication enforcement, rate limiting, security headers, caching, metrics, tracing, health, migrations, or
   deployment artifacts existed.

## 4. Design, by concern

**Security.** Quarkus enforces authentication (`quarkus.http.auth.permission`); `RequestSecurityFilter` builds a verified
`SecurityContext` (principal, tenant, roles). Authorization is deny-by-default through `Secured` → `AuthorizationService`
(role→permission matrix; unknown role grants nothing; denials audited; audit outage never turns a deny into an allow).
Voids/discounts/waivers need separate permissions (`OrderVoid`, `ReservationWaiver`) and the approver is the authenticated
caller, never a name from the request body. Per-(tenant, principal) token-bucket rate limit (in-process: with N replicas
the effective limit is up to N×; put a hard cap at the gateway if needed). Strict response headers, `no-store` on `/api`,
1 MB body limit, management port separate from the public port, `PiiRedactor` for logs/errors, `GuestContact.toString()` masked.

**Tenancy (defence in depth).** (1) token-derived tenant; (2) every repository method takes the tenant explicitly (no ambient
state in domain/application); (3) cache keys include the tenant (`CacheKey.of` forbids a null tenant); (4) PostgreSQL
row-level security on `aggregate_document` and `platform_audit` with a **transaction-local** `app.tenant_id` (cannot leak via
pooled connections). The application DB role must be `NOSUPERUSER NOBYPASSRLS` (`deploy/postgres-init.sql`).

**Caching.** `Cache` port + Caffeine adapter: single-flight loads, failures never cached, negative caching with its own TTL,
and *invalidate-wins* (a load that began before an invalidation cannot repopulate stale data). Published menu cached per
tenant+venue and invalidated **after commit**. Swap in Redis behind the same port if you need a shared cache.

**Observability.** Logs: JSON, MDC `correlationId/requestId/tenantId/principalId`, one access-log line per request using the
route template (never raw paths/queries). Metrics: Micrometer with a **tag allow-list** (`SafeTags`: no tenant/user/ids →
bounded cardinality); handler/use-case duration + outcome, `domain.events.published{type}`, outbox gauges (pending, dead
letters, oldest-pending age). Traces: OpenTelemetry spans per use case (attributes may carry ids). Health: Quarkus datasource
readiness; outbox lag is a *metric/alert*, deliberately not a readiness failure (removing pods would worsen lag).

**Reliability.** Transactional outbox written in the same transaction as the aggregate; relay claims rows with a short lease
(`FOR UPDATE SKIP LOCKED`), publishes with `acks=all` + idempotent producer, marks published after ack, exponential backoff
with jitter, permanent failures dead-lettered immediately, ≥10 attempts parked. Delivery is **at-least-once and not ordered
across retries**: consumers use the inbox and are idempotent. Inbox dedupes by event id; `IdempotentExecutor` backs the
`Idempotency-Key` header (scope includes the tenant).

**Restaurant domain.**
* *venue*: `Venue` (tax/service-charge policy is configuration; IDR defaults PPN 11% + 5% service), `DiningTable`
  (AVAILABLE→OCCUPIED→DIRTY→AVAILABLE, BLOCKED), `Menu` (server-side pricing, publish rules, price-change events).
* *order*: `DineInOrder` with course firing, manager-approved voids/discounts, bill gating (no unsent items), tax rules
  frozen at open, per-component HALF_UP rounding, and `splitEvenly` shares that sum **exactly** to the total.
* *kitchen*: `KitchenTicket` (status derived from items; cannot bump half-cooked food), station routing with load-aware promise
  times, KDS queue ordering, late detection, idempotent fire (deterministic ticket ids), the **86 board** (with expiry).
* *reservation*: `AvailabilityLedger` per venue-day (the consistency boundary: best-fit allocation, table combining up to 3,
  turn-time + cleanup buffer, optimistic version ⇒ no double-booking under concurrency), deposits, free-cancellation window,
  late-fee / forfeiture, no-show grace, manager waiver, reschedule that never loses the original slot, nearest alternative
  slots when full, and a waitlist with hold expiry. Domain events carry ids/counts only, **no guest personal data**.
* Cross-context reactions (`RestaurantPolicies`): course fired → kitchen tickets; item ready → order line ready; line voided →
  ticket void; table cleaned → notify waitlist. In production they run behind the inbox via `RestaurantEventConsumer`;
  `DinnerServiceScenarioTest` runs a whole evening end-to-end through the same policies.

**Persistence.** Aggregates are stored as versioned JSONB snapshots (`aggregate_document`): optimistic locking by
`WHERE version = expected`, queryable columns lifted out, RLS. Same transaction as outbox appends (both use the Hibernate
Reactive session). `PersistenceAnchor` exists only so Hibernate stays enabled (Quarkus disables it with zero entities).
Guest PII retention: `restaurant_anonymize_guests(interval)` (run with a dedicated `BYPASSRLS` maintenance role).

## 5. Runtime topology

`deploy/Dockerfile.jvm` (non-root uid 10001), `deploy/k8s/restaurant.yaml` (3 replicas, zero-unavailable rollout, startup/
liveness/readiness on the management port, read-only root FS, all capabilities dropped, PDB, HPA, NetworkPolicy, topology
spread), `deploy/observability/*` (alerts, OTel collector, Prometheus, Tempo), `deploy/docker-compose.dev.yml`
(Postgres with a non-superuser app role, Redpanda, collector, Prometheus, Grafana), `.github/workflows/ci.yml`
(reactor check, migration apply, build+test, SBOM, OWASP dependency check, secret scan).

## 6. Runbook

<a id="runbook-high-error-rate"></a>**High error rate.** Find the correlation id from a failing response, search logs for it
(JSON `correlationId`), open the trace. 5xx are logged with stack traces server-side only. Check DB pool saturation and
Postgres connectivity first.

<a id="runbook-outbox-lag"></a>**Outbox lag.** Symptoms: kitchen tickets or order-line updates late. Check
`outbox_oldest_pending_age_seconds`, Kafka reachability, relay logs. Rows are claimed with a 30 s lease, so a crashed relay
self-heals. Backlog drains in batches of 50 (up to 20 batches/tick); raise `syirkah.outbox.batch-size` if needed.

<a id="runbook-outbox-dead-letters"></a>**Dead letters** (`attempts >= 10`, unpublished). Inspect, fix the cause, then replay:
```sql
SELECT entry_id, event_type, attempts, last_error, created_at FROM platform_outbox WHERE published_at IS NULL AND attempts >= 10;
UPDATE platform_outbox SET attempts = 0, available_at = now(), locked_until = NULL WHERE entry_id = '<id>';
```
Consumers deduplicate, so replays are safe. Consumer-side poison messages land in `<topic>.dlq`.

**Housekeeping to schedule (not included).** Delete idempotency rows older than ~48 h
(`DELETE FROM platform_idempotency WHERE first_seen_at < now() - interval '48 hours'`), prune published outbox rows and
inbox rows older than your retention, run `restaurant_anonymize_guests(interval '180 days')` nightly.

**Verify RLS in an environment** (should print 0 with no tenant set, and only that tenant's rows with it set):
```sql
SELECT count(*) FROM aggregate_document;
BEGIN; SELECT set_config('app.tenant_id','<tenant-uuid>',true); SELECT count(*) FROM aggregate_document; ROLLBACK;
```

## 7. Known limitations / not built

* Payment is a reference string (`settle(paymentRef)`); wire `payment-fnb`/gateway callbacks to it.
* Notification delivery (SMS/WhatsApp/email for confirmations and "table ready") is not implemented; the events and the
  guest contact are there for a notifier consumer.
* Booking rules and role matrix are global defaults (config / code); per-venue rules and DB-backed grants plug in via
  `BookingRulesPort` and `GrantResolver`.
* Approvals are single-person (a manager performs the void). A "waiter requests, manager approves" flow is not built.
* Inventory deduction/costing and shift/rota integration are not wired (events exist to hang them on).
* Rate limiting is per replica. Event ordering is not guaranteed across retries (see §4).
* No ArchUnit tests were added (library unavailable offline); `tools/check-reactor.py` covers declared-dependency and
  domain-purity checks in the meantime.

## 8. First things to do after merging

1. `mvn -B verify` and fix compile-level issues in the 35 unverified files (most likely: Hibernate Reactive native-query
   parameter binding, `ContextLocals`/MDC propagation, Kafka metadata builder, config-mapping names).
2. Integration tests on a real stack (Testcontainers Postgres + Kafka): tenant isolation through the HTTP layer
   (token A cannot reach tenant B with any header), RLS with the real app role, outbox → Kafka → consumer → inbox round trip,
   and that MDC fields / tenant context survive Mutiny thread hops.
3. Create the app DB role `NOSUPERUSER NOBYPASSRLS`; give the maintenance job a separate role.
4. Add the token claims `syirkah_tenant_id`, `syirkah_tenants`, `syirkah_user_id` and `groups` in your IdP.
5. Load-test reservations (hot day, one venue) and tune ledger retry count / pool sizes.


## 9. Ride-hailing addendum (summary; details in `MOBILITY.md`, gaps in `GAPS.md`)

* New: `aggregate/` (kernel + Postgres persistence, shared), `mobility/` (kernel, pricing, wallet, driver, ride, integration, persistence, api), migration `V300__mobility.sql`,
  alerts (SOS pages), `deploy/k8s/mobility.yaml`, `application.properties` for the service, permissions for `rider`, `driver`, `ops-admin`, `payment-gateway`.
* Replaced existing file added since §2: none beyond the nine listed (the `Secured` helper moved from `restaurant-api` to `security-adapter`).
* Findings added: money in JSON lost its scale through Jackson's tree model (`28000` -> `2.8E+4`), now prevented by `JsonMapperConfig` / `MoneySafeJsonCustomizer`;
  rolling deployments need `FAIL_ON_UNKNOWN_PROPERTIES=false` for snapshots (also set there), so **new snapshot fields must be nullable or defaulted**.
* Migration order: V100 (platform) -> V200 (aggregate documents) -> V201 (restaurant retention) -> V300 (mobility) -> V301 (traces, retention and erasure functions).
* Second pass added: trip evidence and share links, refunds, earnings statements, chat, fraud signals, top-ups with verified provider callbacks, payouts, privacy/retention SQL, public endpoints (per-IP rate limited), a nightly maintenance CronJob (`deploy/k8s/maintenance-cronjob.yaml`, needs a `BYPASSRLS` maintenance role), new alerts. See `MOBILITY.md`.
