# Affiliate & Partner Capability — M1 Repository Audit

- **Status:** Evidence-based discovery; no production affiliate code added
- **Repository reviewed:** `andalus-ai/numu`
- **Baseline reviewed:** `main` as observed on 2026-10-28
- **Related plan:** [Affiliate & Partner Capability Roadmap](AFFILIATE-PARTNER-CAPABILITY-ROADMAP.md)
- **Purpose:** Decide the first implementation boundary from existing source, not from module names alone.

## Executive decision

Create a **dedicated, reusable Affiliate & Partner capability** for partner-program lifecycle, referral-link attribution, commission calculation and lifecycle, adjustments/disputes, and payout orchestration. Do not implement affiliate commissions as a new responsibility inside CRM, Promotion, Payment, or Wallet.

Integrate with those capabilities through narrow public contracts/adapters. Start as a capability that can be composed in-process; do not mandate a standalone microservice. A dedicated service can be a later deployment choice after operational needs justify it.

This is an architectural decision for the next milestone, not a claim that the capability already exists or that the current integration points are all production-ready.

## Evidence from the repository

### 1. CRM already has a referral aggregate — reuse the concept, not its lifecycle

Observed files:

- `crm/numou-crm-domain/src/main/java/tech/kayys/numou/crm/domain/referral/Referral.java`
- `crm/numou-crm-domain/src/main/java/tech/kayys/numou/crm/domain/referral/ReferralStatus.java`
- `crm/numou-crm-domain/src/main/java/tech/kayys/numou/crm/domain/repository/ReferralRepository.java`
- `crm/numou-crm-application/src/main/java/tech/kayys/numou/crm/application/api/command/CreateReferralCommand.java`
- `crm/numou-crm-application/src/main/java/tech/kayys/numou/crm/application/referral/CreateReferralHandler.java`
- `crm/numou-crm-application/src/main/java/tech/kayys/numou/crm/application/referral/ConvertReferralHandler.java`
- `crm/numou-crm-adapter/src/main/resources/db/migration/V5__crm_referrals.sql`

The CRM `Referral` is documented as a **business introduction** from an account to a lead or opportunity. It has a referrer account, target/type, code, description, and pending/accepted/converted lifecycle. The observed schema stores target lead/opportunity IDs and timestamps.

The inspected model does not represent affiliate-program membership, attribution windows or touchpoints, competing attribution decisions, immutable commission-rule snapshots, commission liabilities, refunds/chargebacks, disputes, or payout reconciliation. Its `CreateReferralHandler` publishes domain events through the CRM event publisher inside a unit of work, but the inspected files alone do not establish durable outbox guarantees for every runtime.

**Decision:** CRM remains the owner of CRM introductions and lead/opportunity conversion. Affiliate may reference a CRM referral or invoke a CRM-facing adapter when a business workflow needs both; affiliate attribution and financial commission state must not be added to the CRM aggregate by default. A later adapter must use a documented public application contract rather than CRM persistence entities.

### 2. Promotion owns discounts, not partner earnings

Observed files:

- `commerce/promotion/README.md`
- `commerce/promotion/numou-promotion-domain/`
- `commerce/promotion/numou-promotion-application/`
- `commerce/promotion/numou-promotion-spi/`

The promotion engine evaluates versioned promotion candidates against cart/customer/channel snapshots and produces discount benefits. Its README lists create/activate/suspend/rule-edit/apply/calculate flows and identifies coupons/quotas and some campaign lifecycle features as deferred.

**Decision:** Promotion can remain a source of customer-facing incentives or provide a discount/campaign reference through an adapter. It must not own partner commission calculations, earned liabilities, commission reversals, or payouts. Customer discount and partner commission are distinct money flows with different eligibility, accounting and audit requirements.

### 3. Commerce has an existing marketplace and an explicit payment boundary

Observed files:

- `commerce/marketplace/numou-marketplace-application/src/main/java/tech/kayys/numou/commerce/marketplace/application/checkout/MarketplaceCheckoutPaymentPort.java`
- `commerce/marketplace/numou-marketplace-application/src/main/java/tech/kayys/numou/commerce/marketplace/application/checkout/PaymentCapabilityCheckoutAdapter.java`
- `commerce/marketplace/numou-marketplace-application/src/main/java/tech/kayys/numou/commerce/marketplace/application/IdempotentMarketplaceOrderCreationService.java`

Marketplace checkout already defines a provider-neutral payment port and has an adapter over the Finance Payment public API. The adapter delegates tenant checks, idempotency, persistence, provider execution and verified outcomes to Payment rather than directly mutating payment records.

**Decision:** Affiliate order attribution should link to a stable source business reference and consume only the minimal verified lifecycle facts needed to qualify/reverse a commission. It should not own order or checkout state, read marketplace tables directly, or treat a browser redirect as proof of payment. The marketplace integration should be an adapter, not a hard dependency in the affiliate domain.

### 4. Finance Payment has a public application API; use it for execution, not commission accounting

Observed files:

- `finance/payment/numou-payment-api/src/main/java/tech/kayys/payment/api/PaymentApplicationPort.java`
- `finance/payment/numou-payment-api/src/main/java/tech/kayys/payment/api/CreatePaymentObligation.java`
- `finance/payment/numou-payment-api/src/main/java/tech/kayys/payment/api/RequestPaymentRefund.java`
- `finance/payment/numou-payment-postgres-adapter/src/main/java/tech/kayys/payment/adapter/postgres/PostgresPaymentApplicationAdapter.java`

The public port exposes create-or-get with idempotency, tenant-scoped lookup, verified outcome application and refund requests. Its contract explicitly says identifiers are not authorization and timeouts are not proof of failure.

**Decision:** Use Payment for provider-facing payout/payment execution only if its public contract is extended or an appropriate payout/disbursement contract is verified. Do not repurpose a customer payment obligation as a partner payout, and do not claim that the payment API already supports affiliate payouts. Payout operations need their own lifecycle and stable business correlation.

### 5. Finance Wallet exists, but observed behavior is mobility-oriented

Observed files:

- `finance/wallet/numou-wallet-domain/src/main/java/tech/kayys/numou/wallet/domain/Wallet.java`
- `finance/wallet/numou-wallet-domain/src/main/java/tech/kayys/numou/wallet/domain/Payout.java`
- `finance/wallet/numou-wallet-application/src/main/java/tech/kayys/numou/wallet/application/PayoutService.java`
- `docs/architecture/ADR-006-finance-wallet-capability.md`

The inspected wallet model describes rider, driver and platform balance/overdraft policies; the payout aggregate is specifically documented as a driver's withdrawal. ADR-006 proposes a shared wallet kernel but explicitly labels it a target for incremental implementation, not a ready-made general wallet contract.

**Decision:** Do not write commission balances directly into this wallet or assume its current payout service is a general partner disbursement API. Affiliate owns commission eligibility and payable state. If partner funds are held/transferred through Wallet in a later milestone, that must use a verified public contract and an explicit policy for the legal/operational meaning of the funds. Avoid a second balance ledger.

### 6. Accounting, identity and tenancy are adjacent capabilities, not affiliate subdomains

Observed artifacts include:

- `finance/accounting/numou-accounting-payables/` and the accounting reactor
- `identity/numou-identity-application/src/main/java/tech/kayys/numou/identity/application/port/`
- `tenancy/tenancy-spi/src/main/java/tech/kayys/numou/tenancy/spi/port/`

The repository has accounting payables, identity authorization/current-principal ports, and tenant repository/membership/settings ports. This audit did not verify a public affiliate-specific accounting posting contract, general-purpose disbursement contract, or an affiliate-ready durable event contract.

**Decision:** Affiliate must carry tenant/actor/correlation and audit context and expose an accounting integration port for recognized commission liabilities/adjustments. It must not post journal entries itself or reach into accounting persistence. Identity and tenancy integration must be through public authorization/membership contracts or the host application's equivalent, not identifier possession.

### 7. No existing affiliate capability was found in the inspected source tree

The recursive repository tree was searched for affiliate, referral and commission names. It surfaced CRM referral models, Syirkah partners and courier network partners, but no dedicated affiliate-program/attribution/commission capability.

This is evidence from the reviewed tree, not proof that every business use case or branch has been exhaustively searched.

## Boundary map

| Concern | Owner | Affiliate integration rule |
|---|---|---|
| Affiliate programs, partner memberships, referral links, attribution decisions | Affiliate capability | Own these invariants and history |
| CRM referral to lead/opportunity | CRM | Link by stable public reference; don't duplicate CRM lifecycle |
| Customer discounts/coupons | Promotion | Optional incentive adapter; discounts are not commissions |
| Orders, fulfilment and source transaction facts | Source capability (e.g. Marketplace) | Consume versioned events/contracts or a reconciled adapter |
| Payment-provider execution/refunds | Finance Payment | Call public API; do not own provider state |
| Operational wallet balances/holds/movements | Finance Wallet (subject to verified public contract) | Never duplicate its ledger; don't assume current mobility payout is generic |
| Journal entries, payables, reconciliation | Finance Accounting | Post through explicit integration contract |
| Identity, authorization and tenant membership | Identity / Tenancy / host composition | Enforce tenant and actor authorization at application boundaries |
| Durable delivery, retries and replay | Verified event/outbox infrastructure or adapter | Establish guarantees before relying on event delivery |

## M1 decision and implementation sequence

1. **M2 — Core program and partner foundations:** establish the capability's domain/application/SPI boundaries and the actual Maven reactor root after checking neighboring reactor POM conventions. Implement tenant-scoped programs, partner identity/reference, program membership, versioned referral codes/links, and lifecycle invariants. Do not add an HTTP runtime or database adapter unless the selected reactor conventions and composition require it.
2. **M3 — Attribution:** add explicit touchpoints, attribution policy/version, conversion eligibility, deterministic tie-breaking, expiry/window behavior and idempotent decisions. Keep source-event ingestion and reconciliation behind ports.
3. **M4 — Commission lifecycle:** introduce immutable commission calculation snapshots, precise money/currency rules, pending/approved/held/reversed states, linked adjustments, and idempotent source-event handling. Do not mark a commission payable from an unverified event.
4. **M5 — Finance integration:** add public ports/adapters for accounting liability posting and a verified payout/disbursement path. Keep commission accrual distinct from payment execution and wallet movement.
5. **M6/M7 — Operations and readiness:** partner/operator views, dispute and fraud controls, audit/reconciliation reports, privacy/retention and Indonesia localization/legal review.

## Acceptance criteria before M2 is called complete

- Domain rules compile/test without HTTP, database, broker, or provider SDK dependencies.
- Tenant scope is explicit on persisted identities and repository operations; cross-tenant reads/writes are tested.
- Idempotency key reuse with a different request fingerprint is a conflict; exact retries do not duplicate decisions, commissions, events, or payouts.
- Rule versions and calculation inputs are snapshotted so future edits cannot silently rewrite historical commissions.
- Commission reversal is linked to the source reversal and original commission; no destructive mutation of settled history.
- Money precision/currency and rounding are explicit and tested.
- No direct access to CRM, marketplace, payment, wallet or accounting persistence internals.
- Tests run in the actual Maven reactor and results are reported from tool output; no root build is assumed.

## Scope and verification limits

This was a repository-tree and targeted-source audit. It did not run Maven, inspect every adapter/runtime, validate production event-delivery guarantees, or establish legal/tax compliance. The next implementation milestone must inspect the selected reactor POMs and current CI workflow before adding artifacts or reporting build results.
