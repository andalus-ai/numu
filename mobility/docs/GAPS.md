# What is still missing before a real launch

Status after two build passes. **Built** = implemented and covered by the 415 passing tests (framework wiring excepted, see item 1).
Items are ordered by how much they block a launch.

## Closed since the first gap list (built and tested)
GPS trace storage with polyline evidence and removal of per-fix ride rewrites · route-deviation detection · "share my ride" links · support refunds with caps ·
driver earnings statements · in-app chat with number hiding · fraud/collusion signals · top-up intents with verified provider callbacks (Midtrans, Xendit, generic HMAC) ·
payout lifecycle with exactly-once reversal · public-endpoint security (per-IP limits, no login) · retention purge and rider erasure (SQL, tested on PostgreSQL) ·
nightly maintenance CronJob · address labels carried through booking · separate PIN secret · ledger ordering by wallet version · money-safe JSON.

## P0: still blocks launch

**1. Compile and integration-test the framework layer (about 60 files).** Everything touching Quarkus, Vert.x, Hibernate Reactive, Kafka, Micrometer or OpenTelemetry
was written without those libraries and has never been compiled or run (the business logic beneath it is tested). Budget a real pass: fix compile errors, then
Testcontainers tests (Postgres + Kafka + an OIDC server) for: tenant isolation through HTTP, RLS with the real application role, outbox -> Kafka -> consumer -> inbox,
tenant/MDC context across Mutiny thread hops, SSE, the public and webhook endpoints (including that `permit` paths really skip authentication and are still rate-limited).

**2. Verify the payment providers against their sandboxes.** The Midtrans signature rule, Xendit callback token, and the Snap/Invoice HTTP calls were written from the
providers' public descriptions and are tested only against my own implementation of them. Run each provider's sandbox end to end (create charge, pay, receive callback,
expiry, partial/over-payment, refunds) and confirm field names and status mappings. Add an IP allow-list for callbacks if the provider publishes one.

**3. Automate real money out.** Payouts are *recorded and reversible*, but the transfer itself is a manual bank batch by finance (`ManualPayoutProvider`). To automate: a
disbursement-API adapter, bank-account verification (name match), payout schedule/limits, failure and return handling, reconciliation of provider settlement reports against
the ledger, a general-ledger export, VAT on platform fees and driver income-tax reporting.

**4. Legal and regulatory (not engineering).** Holding rider/driver balances is e-money/payment activity in Indonesia (Bank Indonesia licensing, or a licensed partner holds the
funds); transport platforms have licensing, tariff-band and driver-status rules; PDP-law obligations (consent records, breach process, DPO, data-subject request handling: the
erasure and retention functions exist, the request workflow and audit trail around them do not).

**5. Rider-driver voice and push.** Chat exists; masked calling does not (phone numbers are deliberately never exposed, so there is no way to phone). `RideNotifications`
only logs: wire FCM/APNs (offers need sub-second delivery; polling `GET /driver/offer` works but is slow and wasteful at scale).

**6. Driver onboarding and KYC.** Approve/reject exists. Missing: document capture and verification (ID, licence, vehicle registration, insurance), liveness/selfie, background
checks, vehicle inspection, periodic re-verification, and the ops UI to review them.

**7. Safety operations.** SOS and route-deviation raise events and alerts. Missing: an ops console showing the live ride and letting a human act, emergency-contact
notification, an incident workflow, optional audio recording, authority integration. A staffed response process matters more than the button.

**8. The clients and the back office.** No rider app, driver app or ops/admin console exists (the API is complete). Also needed: tariff, surge-zone and promo management UI
(today code defaults and SQL), support tooling beyond refunds (disputes, lost items), reporting/BI.

## P1: needed to run it well
* **Load, soak and chaos testing** (none done): 10k drivers at one fix per 4 s, dispatch latency in a demand spike, sweeper contention across pods, Kafka lag, failover.
  The ride-document write amplification is fixed (trace is insert-only), but chat messages and offers still rewrite small documents.
* **Location scale**: Postgres UNLOGGED is fine to a few thousand updates/s; plan Redis GEO behind `DriverLocationIndex` before you need it.
* **Maps**: deploy/size OSRM or a commercial API with traffic-aware ETAs; map-matching of GPS; geocoding and autocomplete (the API takes coordinates and free-text labels).
* **Multi-city**: one circular service area and one tariff today. Needed: per-city areas (polygons), tariffs, surge zones, airport/station rules, tolls, operating hours.
* **Fraud depth**: device attestation (Play Integrity / App Attest), mock-location detection, promo budgets and per-device limits, account-takeover protection, chargebacks.
  Current signals are cheap heuristics and only *flag*; tune thresholds on real data.
* **Dispatch quality**: sequential nearest-ranked offers is a solid baseline; batch matching, driver destination mode, adaptive timeouts and pre-positioning incentives come next.
* **Operations**: Grafana dashboards, SLOs (time-to-match p95, booking success), on-call runbooks, backups with tested PITR restore, multi-AZ and DR, Kafka retention and DLQ process,
  advisory-lock leadership for the sweeper (currently every pod sweeps; safe but redundant), external penetration test, WAF/DDoS at the edge.
* **Data lifecycle**: partition/archive `aggregate_document` and `wallet_ledger` by month (retention covers traces, demand, idempotency, outbox/inbox only).

## P2: product breadth (not started)
Scheduled rides, shared rides, multi-stop, delivery/food, corporate accounts, driver incentives/bonuses (statement category exists, no engine), referral programmes,
in-app support chat, multi-language, accessibility.

## Known limitations of what is built
* Wallet hold is 1.2x the fare; a metered trip can exceed it and the difference becomes rider debt (blocks the next booking). A destination change does not resize the hold.
* Flat commission rate and fixed 80/20 cancellation-fee split; no per-driver tiers; no promo budget caps or stacking.
* Fraud signals are heuristics with fixed thresholds; a regular commuter legitimately riding the same driver can trip PAIR_REPEAT (it only flags).
* Chat number-hiding catches digit runs, emails and links; deliberately spelled-out numbers ("zero eight one...") pass.
* Share links reveal live driver position to anyone holding the link until it expires; the rider chooses the duration (max 6 h) and the link can only be created, not revoked.
* Erasure keeps wallet-ledger rows (financial records, pseudonymous ids only) and coarsened ~1 km coordinates for analytics; legal review should confirm that is acceptable.
* The pickup PIN secret must not be rotated while rides are in progress (rotate the quote secret freely).
* Ratings: a driver's rating of a rider is stored on the ride only; there is no rider reputation or blocking.
* Event ordering is not guaranteed across outbox retries (consumers are idempotent); the in-process rate limiters are per replica.
* Tariffs in `FareSchedule.indonesiaDefaults()` are illustrative placeholders, not a published or legally reviewed tariff.

## Defects found and fixed along the way (so you know they existed)
JSON `BigDecimal` scale loss (`28000` returned as `2.8E+4`) · ledger entries ordered by timestamp could mis-state the closing balance (now ordered by wallet version) ·
lazy-evaluation bugs that only appear with real (non-synchronous) Mutiny · flaky privacy assertions that scanned random UUIDs · a driver-release race on duplicate accepts ·
wrong-PIN counters rolled back by exceptions · shared secret for quotes and PINs · per-GPS-fix ride rewrites · maintenance functions callable by the application role.
