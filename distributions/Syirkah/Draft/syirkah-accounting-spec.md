# Syirkah Accounting — Domain and Fiqh-Aligned Specification

> Status: implementation-oriented domain specification. This document describes the current domain model and the principles it is intended to enforce. It is not a fatwa or a substitute for review by qualified Shariah advisers; details can differ by madhhab, contract type, jurisdiction, and the facts of a transaction.

## 1. Purpose and scope

The Syirkah domain records an akad, partner capital movements, period results, profit/loss allocations, settlements, corrections, and liquidation. Its purpose is to make the accounting trail explainable and auditable without turning an ordinary business loss into an automatic personal debt of a partner.

The aggregate is the authority for:
- the active and historical contract versions;
- append-only partner movements and their hash chain;
- period-close snapshots and their hash chain;
- derived partner positions and reconciliation;
- liquidation outcome and its audit fingerprint.

The domain is not a general ledger replacement by itself. Accounting adapters map movements to journal posting plans; persistence adapters must store the complete snapshot and must call the aggregate restore/integrity path when rehydrating it.

## 2. Governing principles

The model aims to reflect these general muamalah principles, subject to the actual akad and applicable scholarly interpretation:

1. **Consent and clarity of akad.** Contract terms, roles, capital, profit basis, nisbah, witnesses, and recorded ijab-qabul are explicit and fingerprinted.
2. **Profit is a share of realized net profit, not a guaranteed fixed amount.** Profit sharing is based on the contract's nisbah and the period's net result, after the modeled costs.
3. **Ordinary capital loss follows capital risk.** For capital-based syirkah, a loss is allocated by eligible capital rather than by the profit-sharing nisbah. The exact rule depends on the partnership form and valid akad.
4. **Mudharabah distinguishes capital risk from manager misconduct.** Ordinary business loss is borne by the capital provider, subject to the capital remaining at risk. A manager's liability is recorded only where negligence or breach is evidenced and attributable; it is not inferred merely because the business lost money.
5. **No invented partner debt for losses beyond eligible capital.** If loss exceeds the remaining eligible capital and any evidenced fault liability, the uncovered amount remains an entity-level deficit in the model. It is not silently converted into partner liability.
6. **External creditors precede partner distributions at liquidation.** Available proceeds pay external debts first; remaining value is applied to partner claims and the liquidation result.
7. **Changes are prospective and auditable.** A contract revision creates a new version, requires a prior close, effective date after the closed period, and approval from all current and incoming partners.
8. **Corrections are explicit, not destructive edits.** A corrected close reverses the prior close's movements and records a replacement linked to the superseded close.

These are domain-level guardrails. They do not determine whether a real-world transaction is Shariah-compliant without reviewing the full facts and documents.

## 3. Contract model

A `SyirkahContract` records:
- contract version and type;
- currency, partners, roles, and work/capital responsibilities;
- capital contributions;
- profit-sharing nisbah;
- profit basis, which must be net profit rather than revenue;
- loss-sharing configuration where the partnership type permits it;
- declared clauses, witnesses, ijab-qabul record, and effective date.

The canonical contract representation is deterministic: reordering semantically unordered collections should not change the fingerprint, while changing a material akad term should. Contract revisions cannot change the syirkah type or currency in place.

Forbidden or invalid clauses are rejected by validation. A validation result is a software rule, not a complete legal opinion about every possible clause or local interpretation.

## 4. Period-close calculation

For each period:

`net result = revenue - direct costs - operating costs - other costs`

All inputs must use the akad currency and amounts must fit the aggregate's configured decimal scale. Periods may not overlap the prior active close.

### Profit

- First restore impaired capital according to the allocator's capital-restoration rule.
- Allocate the remaining distributable profit according to the agreed nisbah.
- Depending on the close disposition, allocated profit is either payable to partners or retained as partner capital.
- A fixed guaranteed profit amount is not used as the allocation basis.

### Loss

- For capital-based forms, allocate ordinary loss against eligible capital in accordance with the configured rules.
- In mudharabah, allocate ordinary loss against the capital provider's eligible capital.
- Where a valid manager-fault finding exists, only the evidenced attributable amount is assigned as manager liability; it is deducted from the loss before the remaining eligible capital allocation.
- Loss beyond eligible capital is exposed as `unallocatedLoss` / entity deficit. It does not create negative partner capital or automatic partner liability.

The allocator uses deterministic apportionment so rounding does not create or destroy the amount being apportioned. Where a capacity limit prevents further allocation, the uncovered balance remains visible as entity deficit.

## 5. Partner position and accounting movements

Positions are derived by folding movements rather than trusting mutable balance fields. The position buckets track:
- **basis** — capital/profit basis used for capital accounting;
- **capital balance** — current capital remaining after returns and allocated losses;
- **payable** — profit allocated but not yet paid;
- **liability** — amounts owed because of a separately recorded liability, such as evidenced manager fault or an overpayment after a close correction.

Movement history is append-only. Reversals are new movements that reference the original sequence; historical records are not deleted to make the books appear as if the original event never happened.

A correction is limited to the latest effective close and is refused when later movements other than profit distributions depend on the original result. Where a corrected entitlement is lower than profit already distributed, the excess becomes a liability; it is not represented as a negative payable. Settled liabilities cannot be erased by correcting the originating close.

## 6. Liquidation waterfall

Liquidation must be started before completion. The domain requires the syuf'ah offer flag to be recorded before liquidation completion; this flag is evidence of the recorded process, not independent proof that every legal requirement for syuf'ah has been met.

The model follows this sequence:
1. Convert assets/cash on hand into proceeds.
2. Pay external debts first; residual proceeds may be negative if debts exceed proceeds.
3. Calculate partner claims from remaining capital balance plus unpaid profit.
4. Determine the net liquidation result after claims.
5. Allocate profit or loss using the same contract-aware allocator, including only valid evidenced manager-fault liability.
6. Record partner cash received, amounts owed, and the final liquidation audit hash.

If proceeds cannot cover external debts and eligible partner claims, the shortfall is not automatically posted as a debt of every partner. Any separately enforceable liability must have its own valid basis.

## 7. Audit and restore invariants

The aggregate's integrity check verifies:
- contiguous movement sequence and movement hash chain;
- close sequence/hash links and close fingerprints;
- liquidation fingerprint, including its inputs, allocation lines, payouts, and completion time;
- derived position buckets against the movement fold;
- equity reconciliation, including active close and liquidation entity-level deficits.

New liquidation results use a versioned full-outcome fingerprint. Legacy persisted results may be checked with the legacy movement-head scheme for compatibility. Persistence adapters should not bypass `Syirkah.restore(snapshot)`; a snapshot that fails integrity verification should be rejected rather than silently repaired.

A hash chain is tamper-evidence, not a digital signature or proof of who authorized a transaction. Production systems should also protect storage access, retain authorization records, and use appropriate key/signature controls where required.

## 8. Contract revision and lifecycle

- Opening requires a valid version-1 contract and records initial capital contributions.
- Closing requires an active aggregate, unique close ID, and a non-overlapping period.
- Revision requires a close, a next-version contract, unchanged type/currency, a later effective date, and unanimous approval from all current and incoming partners.
- Departing partners must settle open payables/liabilities before departure.
- Liquidation terminates the aggregate; subsequent close or repeated liquidation attempts are rejected.

## 9. Important test scenarios

The domain test suite should retain coverage for at least these cases:

- net profit is allocated on net result, not revenue;
- nisbah profit allocation differs from capital-proportion loss allocation;
- impaired capital is restored before additional profit is distributed;
- losses that exceed capital become entity deficits without negative equity or fabricated partner debt;
- mudharabah ordinary loss is distinguished from evidenced manager fault;
- a fault liability is separate from the entity's remaining uncovered deficit;
- explicit loss allocations never overdraw eligible capital;
- contract fingerprints bind material akad terms and ignore irrelevant collection ordering;
- revisions require close, later effective date, and unanimity;
- corrections reverse and supersede the old close, preserve prior audit evidence, and convert overpayments into liabilities;
- liquidation pays external debts first and accounts for partner claims, deficits, and manager liability separately;
- altered movement, close, liquidation allocation, payout, or persisted snapshot is rejected by integrity verification;
- all monetary inputs use the akad currency and configured decimal scale;
- journal posting plans balance for each movement.

## 10. Known boundaries and review checklist

Before production use, review the implementation against the exact akad and governing Shariah policy, including:
- the chosen treatment of each partnership form and any explicit loss-sharing clause;
- how capital-in-kind, valuation changes, impairment, receivables, and non-cash assets are evidenced;
- whether and how syuf'ah applies to the actual asset and transaction;
- debt priority, insolvency, guarantees, collateral, and creditor rights under applicable law;
- evidence and due process for negligence/breach findings;
- tax, regulatory reporting, and the mapping from domain movements to the organization's chart of accounts;
- migration compatibility for legacy snapshots and audit hashes.

The implementation should make uncertain or unsupported cases explicit. It should not infer misconduct, guarantee capital or profit, or hide an unfunded deficit simply to force reconciliation to zero.
