# Ride-hailing suite (Grab/Gojek-style): design and operations

Bounded contexts under `mobility/`, on the shared `aggregate/` kernel (versioned aggregates, optimistic locking, outbox, Postgres
JSONB documents with row-level security), the same security / observability / reliability platform as the restaurant suite.

| Context | What it owns |
|---|---|
| `mobility-kernel` | Geo maths, 0.01° grid cells, vehicle types, ids, GPS plausibility, routing port + straight-line fallback |
| `mobility-pricing` | Upfront vs metered fares, surge policy, promos, cancellation policy, **settlement maths** (pure, property-tested) |
| `wallet` | Rider/driver/platform wallets: holds, forced captures (debt), overdraft limits, idempotent operations, sharded platform account, append-only ledger |
| `driver` | Verification, online/offline, one-trip-at-a-time claim, acceptance/rating stats, cancellation suspensions, live-location index |
| `ride` | The `Ride` aggregate (offers, PIN start, geofences, tracking, fare finalisation, cancellation, reassignment), one-active-ride lock, quote tokens, surge service, dispatch, settlement |

## Ride lifecycle

```
SEARCHING -> DRIVER_ASSIGNED -> DRIVER_ARRIVED -> IN_PROGRESS -> COMPLETED
    |  \           |                  |
    |   NO_DRIVERS +-- driver withdraws: back to SEARCHING (max 2x, then CANCELLED by system, free)
    +---------------+-> CANCELLED (rider: free until 2 min after assignment; no-show after 5 min wait; ops/system)
```

1. **Quote** (`POST /rides/quotes`): route (OSRM or fallback) + surge for the pickup cell + promo → one signed token per vehicle type.
   The token (HMAC, bound to tenant and rider, 5-minute expiry) carries the facts the price came from, so a client cannot edit the
   price, swap the pickup, reuse another rider's quote or book after expiry; the server recomputes and refuses if the total moved.
2. **Book** (`POST /rides`, `Idempotency-Key` required): verify token, promo redemption (once per rider), wallet check + **hold of 1.2x the fare**,
   acquire the rider's one-active-ride lock, create the ride, then dispatch.
3. **Dispatch**: sequential offers, 15 s each. Candidates from the location index (2 km, then 3.5, then 5), ranked by
   `ETA x (1 + (5-rating) x 0.05) / max(acceptanceRate, 0.3)`; cash rides skip drivers at their commission-debt limit. Accept = ride
   version + driver version (one trip at a time); a failed accept undoes the driver claim unless the ride is assigned to that very driver.
4. **Pickup**: arrival is geofenced (150 m); the trip starts only with the rider's 4-digit PIN (5 attempts; wrong attempts are *saved*, then locked).
5. **Trip**: GPS fixes accumulate distance; teleports/out-of-order fixes ignored. At completion: within tolerance (distance 70-120%,
   time 60-150%) the upfront price stands; otherwise metered at the locked surge, capped at 1.5x the quote; unreliable tracking falls back to upfront.
6. **Settlement** (inline after the ride ends, again from the event, again from a sweeper; idempotent by wallet refs):
   `rider pays R = F - D + B (+ tip)`, `commission = F x rate`, `driver earns E = F - commission (+ tip)`, `platform = R - E`.
   A promo never reduces the driver's earning. Cash rides debit the driver `R - E` instead of charging the rider. All moves sum to **exactly zero**
   (tested over 20,000 random combinations).

## Added in the second pass (all tested; framework wiring uncompiled)

| Area | What it does |
|---|---|
| **Trip evidence** | Every accepted GPS fix is appended to `trip_trace_point` (insert-only). The ride document is **no longer rewritten per fix**: the final distance is computed from the trace at completion. `GET /rides/{id}/trace` (rider, driver, ops) returns a 10 m-simplified Google-polyline string (a few hundred bytes). |
| **Route deviation** | Three consecutive fixes outside the pickup-to-drop-off corridor (max of 1.5 km or half the trip length) raise one `route-deviated` event per ride; the ride is saved only when that state changes. |
| **Share my ride** | `POST /rides/{id}/share` returns a signed, expiring (max 6 h) token; `GET /public/track/{token}` shows status, driver *first name*, vehicle, plate and live position only: no addresses, PIN, phone or fare. Public, per-IP rate-limited, every failure is the same 404. |
| **Support refunds** | `POST /ops/rides/{id}/refund`: capped at what the rider paid across all cases, idempotent per case reference, optional driver clawback, audited. A half-finished refund is repaired by repeating the request. |
| **Earnings statements** | `GET /wallet/driver/statement`: built from the ledger (ordered by the wallet's own version, not timestamps): ride earnings, cash commission owed, tips, cancellation fees, adjustments, payouts; `net = closing - opening`, always equal to the wallet. |
| **In-app chat** | Rider and driver only (anyone else: 404), open from assignment until 30 min after the ride; 500 chars, 200 messages, 1 msg/s per sender; phone numbers, emails and links are replaced so conversations and disputes stay on the platform; text never appears in events. |
| **Fraud signals** | On completion: repeated rider-driver pairing, promo on a sub-1 km trip, promo velocity, no movement. Score >= 60 raises `ride.flagged` for ops; nothing is blocked automatically. |
| **Top-ups** | `POST /wallet/topups` creates an intent and the provider's payment page. The wallet is credited **only** by a verified callback for exactly the intent's amount (Midtrans signature, Xendit callback token, or timestamped HMAC for partners), idempotent on the intent: six concurrent redeliveries credit once. Wrong amount: parked for ops, never credited. A payment that arrives after expiry is still credited. |
| **Payouts** | `POST /wallet/driver/payout` (idempotency key) debits at once; a sweeper marks requests submitted; finance transfers and records the result in the ops console; **a failed transfer returns the money exactly once**; a "failure" after "paid" is refused for a human. Bank accounts are opaque provider tokens, never stored. |
| **Privacy & retention** | `mobility_erase_rider` (pseudonymises finished rides: addresses cleared, coordinates coarsened to ~1 km; deletes traces; erases chat text) and `mobility_purge_expired` (traces 90 d, demand, idempotency, outbox/inbox), run nightly by a `BYPASSRLS` maintenance role (`deploy/k8s/maintenance-cronjob.yaml`). |
| **Address labels, key split** | Pickup/drop-off labels are signed into the quote and shown only to the rider and the assigned driver. Pickup PINs use their own secret (`MOBILITY_PIN_SECRET`) so rotating the quote secret never changes a live ride's PIN. |

## Safety and abuse controls implemented
Ownership checks (not-found, never forbidden), PIN with lockout, geofenced arrival/start, GPS plausibility, signed quotes, one active ride per rider,
promo once-per-rider with refund on cancel, driver suspension after 3 cancellations in 24 h, driver cash-debt limit, rider debt blocks booking,
wallet ledger append-only, coarse locations in offers, no coordinates/addresses/phones/PINs in events or logs, rate limiting, RLS on every table.

## API (all under `/api/v1`, bearer token; roles `rider`, `driver`, `ops-admin`, `payment-gateway`)
`POST /rides/quotes` · `POST /rides` · `GET /rides/{id}` · `GET /rides/{id}/stream` (SSE) · `POST /rides/{id}/{cancel,rating,tip,destination,sos}` ·
`GET /rides/{id}/{trace,chat}` · `POST /rides/{id}/{share,chat}` · `GET /public/track/{token}` (no login) ·
`POST /driver/register` · `POST /driver/{online,offline}` · `PUT /driver/location` · `GET /driver/offer` · `POST /driver/rides/{id}/{accept,decline,arrive,start,complete,cancel,no-show,rating,sos}` ·
`GET /wallet/{rider,driver}` · `GET /wallet/{kind}/history` · `GET /wallet/driver/statement` · `POST /wallet/topups` · `POST /wallet/driver/payout` · `POST /internal/wallet/topup` (gateway account only) ·
`POST /webhooks/payments/{provider}` · `POST /webhooks/payouts` (no login; signature-verified) ·
`GET|POST /driver/rides/{id}/{chat,trace}` · `POST /ops/rides/{id}/refund` · `GET /ops/rides/{id}/trace` · `GET /ops/payouts` · `POST /ops/payouts/{id}/result` ·
`POST /ops/drivers/{id}/{approve,reject}` · `GET|POST /ops/rides/{id}[/cancel]`.

## Data
Aggregates in `aggregate_document` (kinds `ride`, `ride-lock`, `driver`, `wallet`); `driver_location` (UNLOGGED), `ride_demand`, `ride_surge_state`,
`ride_promo`, `ride_promo_redemption`, `wallet_ledger` (V300), `trip_trace_point` plus the retention/erasure functions (V301). Document kinds also include `ride-chat`, `topup`, `payout`. Partial indexes serve exactly the dispatch, offer-poll, settlement-repair and history queries (checked with EXPLAIN).
Ride documents reuse the generic index columns: `venue_id` = driver with the live offer, `ref_id` = rider, `starts_at` = requested-at.

## Configuration that matters
`MOBILITY_SECRET` (quotes, rotate freely) and `MOBILITY_PIN_SECRET` (PINs: rotating changes the PIN of rides in progress, rotate only with none active);
`SERVICE_AREA_*`, `COMMISSION_RATE`, `DRIVER_CASH_DEBT_LIMIT`, `SWEEP_TENANTS`, `OSRM_URL` (unset = straight-line estimator).
Tariffs, surge tiers and cancellation policy are code defaults in `mobility-pricing` (`FareSchedule.indonesiaDefaults()` etc.: illustrative numbers, not a published tariff).

## Operations
* **Sweeper** (every 3 s): expires lapsed offers, moves on, ends searches at 90 s, and every ~30 s repairs unsettled rides. Every pod runs it (safe, redundant).
* **Alerts** (`deploy/observability/alerts.yaml`): SOS (page), no-driver ratio, sweeper idle, settlement repaired, overdrawn wallets, suspension spike, plus the platform ones.
* **Stuck ride**: look at `status`; SEARCHING with no live offer for >2 min means the sweeper or index is down; ENDED but unsettled shows in `ride_unsettled_idx`.
* **Housekeeping to schedule**: prune `ride_demand` (>1 h), idempotency (>48 h), published outbox/inbox; archive old `ride` documents and `wallet_ledger` by month.

## Verification status
**212 of the 415 tests concern mobility** (ride domain 27+9+4, pricing 23, payments 20, dispatch/edge scenarios 30, wallet 22, driver 23, kernel 15, evidence/sharing 8, support/earnings 7, chat/risk 11, permissions 7, others).
Concurrency tests use real threads (racing accepts, double-tap booking, two rides for one driver, concurrent credits, six simultaneous webhook deliveries, concurrent payout requests); they pass repeatedly.
SQL (V300, V301) was run on PostgreSQL 16 as a non-superuser: RLS, redemption and demand races, ledger immutability, partial-index usage (EXPLAIN), erasure and retention.
**About 60 framework files (Quarkus, Vert.x, Hibernate Reactive, Kafka, Micrometer, OpenTelemetry) were never compiled**, and the payment-provider signature schemes and HTTP calls were written from public descriptions and not run against the providers' sandboxes. See `GAPS.md`.
