# ADR-006: Reusable finance wallet capability with domain-specific policies

- **Status:** Proposed for incremental implementation
- **Date:** 2026-10-26
- **Decision owners:** Numou platform maintainers
- **Related decisions:** [ADR-003 — Reusable capability modules](ADR-003-reusable-capability-modules.md)

## Context

Numou has a Mobility wallet aggregate and several finance capabilities, including payment execution, accounting, billing, treasury and Syirkah. The Mobility wallet owns operational balance behavior: currency precision, holds, debit/credit, captures, freezes, overdraft/shortfall and owner-specific behavior. The existing payment core models payment transactions and provider state; it is not by itself a reusable wallet balance and movement ledger. Accounting owns financial posting/reporting, not operational spendable-balance rules.

Other domains may need balances or stored-value-like workflows, but they do not automatically share Mobility's legal, accounting, risk or operational policy. Reuse must not erase those differences.

This decision describes the target boundary. It does not claim that a general-purpose wallet module already exists or that the existing Mobility aggregate is ready to be moved unchanged.

## Decision

### 1. Establish `finance/wallet` as the intended shared capability

The shared capability belongs under the finance capability tree, not at the repository root and not under Mobility. It should be independently consumable as a domain/application contract, while a host may compose it in a standalone app, modular monolith or distributed deployment.

Before creating its Maven artifacts, inspect finance reactor conventions and existing public contracts. Keep the domain free of Quarkus, persistence, HTTP, broker and provider SDK dependencies.

### 2. Share only the stable wallet kernel

The candidate shared kernel may cover:

- account identity and owner references without requiring a particular owner type;
- currency and explicit monetary precision/rounding rules;
- balance and available-balance semantics that are stated in the contract;
- holds/reservations with explicit create, capture, release, expiry and failure behavior where supported;
- immutable, ordered wallet movements and balance-version semantics;
- durable idempotency keys and replay behavior;
- freeze/restriction state and auditable state transitions;
- reconciliation references and integration ports, without embedding a provider or general-ledger implementation.

These are candidate requirements to validate against actual consumers, not a mandate to put every feature in one universal aggregate. Separate models or policy strategies are preferred when invariants differ.

### 3. Keep policy owned by the consuming domain or explicit product profile

A shared wallet must not assume that all accounts can overdraw, that all credits are withdrawable, or that every balance represents regulated stored value.

Examples of policies that remain explicit and selectable/owned outside the neutral kernel:

- Mobility: post-trip fare capture and shortfall, driver earnings, cash-ride commission/debt, platform subsidy, ride settlement and booking eligibility.
- Commerce or hospitality: customer credits, merchant/provider balances, refunds, cancellations, deposits and payout eligibility.
- Logistics or supply chain: partner settlement, operational advances, claims and deduction rules.
- Syirkah or other financial products: contractual profit/loss allocation and participant entitlements; these must not be represented as generic wallet transfers alone.

The kernel may expose extension points and shared movement primitives, but domain modules own eligibility, fees, debt treatment, settlement finality, limits, dispute handling and approval requirements.

### 4. Keep three boundaries distinct

- **Wallet:** operational account balance, available funds, holds and wallet movements.
- **Payment:** initiation and execution through payment methods/providers, asynchronous provider state, refund/reversal requests and callback correlation.
- **Accounting:** balanced journal entries, chart-of-accounts mapping, period controls, financial statements and reconciliation of source movements to postings.

Integrate them through explicit ports, stable references and idempotent workflows. A provider callback must not directly mutate a wallet without authentication/verification, tenant/account correlation, replay protection and a valid state transition. A wallet movement is not automatically an accounting journal entry, and a payment status is not proof that the wallet was credited exactly once.

### 5. Require durable idempotency and concurrency safety

The Mobility implementation currently documents a bounded set of recent operation references. A bounded in-aggregate cache is not sufficient as the sole guarantee for lifetime replay protection. Before extracting or launching a shared wallet, verify durable uniqueness for operation keys, atomicity under concurrent commands, transaction/optimistic-lock behavior, tenant scoping and recovery after process restart.

Define idempotency scope explicitly (tenant, account, operation type and caller-supplied key as appropriate), retention and conflict behavior. Use an inbox/unique constraint or equivalent durable mechanism where required. Reconciliation must detect missing, duplicate and out-of-order external effects.

### 6. Make legal and operational profile requirements explicit

A host must configure and document the applicable product/legal profile before enabling money movement. Profiles may require different KYC/KYB, safeguarding, limits, withdrawal/payout controls, segregation of funds, audit retention, tax treatment, consumer disclosures, dispute handling, reconciliation and regulatory reporting. These are not all universal wallet-kernel rules and must not be silently inferred from the account owner type.

For Indonesia-first deployments, obtain qualified legal/compliance review for the actual product and funds flow before offering regulated stored-value, payment, remittance or payout behavior. This ADR is an engineering boundary decision, not legal authorization.

## Migration plan

1. **Inventory (current gate):** enumerate wallet-like models and consumers across Finance, Mobility, Commerce, Hospitality, Logistics and Syirkah; inspect their actual contracts, persistence, tests and Maven reactor roots. Mark semantics as shared, policy-specific or incompatible.
2. **Contract design:** specify the minimum shared domain vocabulary, monetary rules, movement ordering, idempotency, concurrency, tenant and authorization expectations. Record non-goals and extension seams.
3. **Independent kernel:** add the smallest framework-free domain module and invariant tests. Do not move the Mobility aggregate wholesale or introduce speculative adapters.
4. **Persistence and application contracts:** implement durable movements, account concurrency control, migration strategy, audit and application ports in the owning reactor. Verify replay safety after restart and across concurrent requests.
5. **Compatibility adapter:** adapt Mobility incrementally behind its existing public contracts. Preserve current ride behavior, including explicitly chosen shortfall/overdraft policy, through a Mobility-owned policy/adapter.
6. **Payment and Accounting integration:** use payment execution for provider-side effects and Accounting for journal posting/reconciliation through explicit idempotent ports. Test callback replay, timeouts, refunds, reversals, partial failure and reconciliation repair.
7. **Second real consumer:** onboard one non-Mobility use case only after its requirements are documented. Prove the kernel is reusable without importing Mobility classes or imposing Mobility's policy.
8. **Production gate:** run relevant Maven reactor builds and domain/application/persistence/integration tests; architecture checks; tenant isolation, authorization, audit, concurrency, recovery and migration tests; and operational reconciliation drills. Document any environment-limited verification.

## Acceptance criteria

- The shared wallet domain/application contract has no Mobility, Quarkus, provider SDK or persistence-entity dependency.
- Mobility can retain its domain-specific settlement/shortfall rules without leaking those rules into the neutral kernel.
- Payment, wallet movements and accounting journal postings have distinct identities and state machines.
- Durable idempotency and concurrent balance mutation are tested, not inferred from a bounded in-memory reference set.
- Tenant/account authorization and audit are enforced at application boundaries.
- A second consumer uses the shared contract without inheriting another domain's legal or operational policy.
- Migration compatibility and failure recovery are verified by automated tests.
- Documentation accurately distinguishes proposed architecture from implemented and tested behavior.

## Consequences

- Reuse is encouraged at the stable financial primitive level, not by forcing all business domains into one wallet policy.
- A separate microservice is optional; module boundaries must work in-process first and support remote adapters when operationally justified.
- No existing wallet, payment, accounting or domain policy should be deleted or replaced until the relevant contract and migration tests pass.
