# Numou Syirkah — Islamic partnership accounting

Implements syirkah accounting per *hukum muamalah* on top of numou (DDD + hexagonal, pure-Java domain).

| Module | Contents |
|---|---|
| `numou-syirkah-domain` | `Syirkah` aggregate, `SyirkahContract` (versioned akad + fiqh validation), `ProfitLossAllocator`, `Apportionment`, append-only `PartnerMovement`s, hashed `CloseSnapshot`s, liquidation, `SyirkahPostingPlan` |
| `numou-syirkah-application` | `SyirkahApplicationService`, commands, ports (`SyirkahRepository`, `JournalPostingPort`, `EventPublisherPort`, `TransactionPort`) |
| `numou-syirkah-adapter` | in-memory reference adapters, `JournalEntryPostingAdapter` (posts to numou `JournalEntry`), PostgreSQL `V300__syirkah.sql` |

## Fiqh rules → where enforced

| Rule | Enforcement |
|---|---|
| Profit shared by agreed **nisbah on net profit** (revenue − direct − operating − other costs), never on revenue | `ProfitBasis`, `FinancialResult.netResult()`, `PROFIT_ON_REVENUE` violation |
| Nisbah sums to exactly 100%, every partner has a share | `ProfitSharingRatio` (numeric compare), `NISBAH_PARTNERS` |
| Loss by **capital proportion**; explicit loss ratios rejected for capital-based types | `ProfitLossAllocator`, `LOSS_NOT_BY_CAPITAL` |
| Mudharabah loss on capital provider; manager bears only attributable loss on negligence/breach | `ManagerFault`, `MANAGER_LOSS_LIABILITY` movement |
| Profit exists only above intact capital (profit repairs impaired capital first) | `CAPITAL_RESTORED` before nisbah split |
| Capital is money (goods valued once at akad); mudharib contributes work only; abdan/wujuh have no cash capital; mufawadhah equal capital | `SyirkahContract.validate()` |
| Forbidden clauses (fixed profit amount, capital guarantee, manager salary, …), ≥2 witnesses, ijab-qabul | `ForbiddenClause`, `WITNESSES`, `IJAB_QABUL` |
| Any change of terms → books closed first, unanimous approval, **new akad version**; retained profit is already partner capital | `Syirkah.reviseContract` |
| Retain vs distribute profit at close | `ProfitDisposition` (`PROFIT_RETAINED` moves payable → capital) |
| Liquidation: sell assets (hak syuf'ah offered), pay all debts, then claims; remainder − capital = profit by nisbah or loss by capital; undistributed profit covers later loss | `Syirkah.liquidate` |

## Reports and export

`Syirkah.closeReport(closeId)` (result build-up, frozen terms, per-partner outcome), `capitalRollForward(from, to)` (opening + contributions − returns + retained + restored − losses = closing, per partner) and `statement(partner)`. `CsvExport` writes RFC 4180 CSV and neutralises spreadsheet formula injection in user-supplied text (names, references). PDF/XLSX rendering is left to the presentation layer.

## Governance (maker-checker)

`GovernedSyirkahService` puts four-eyes control on the irreversible operations — akad revision, close correction, start of liquidation and liquidation. A maker proposes; a different checker approves (which executes the exact stored command, once) or rejects with a reason. A failed execution leaves the request pending. Routine operations (close, distribute, settle) stay on `SyirkahApplicationService`. Authentication/authorization of the maker and checker identities is the caller's responsibility; `ApprovalPort` is tenant-scoped and has an in-memory adapter (no SQL table yet).

## Testing

`SyirkahPropertyTest` runs 1,500 seeded random lifecycles (2–5 partners, random nisbah and capital, random close/distribute/correct/settle/liquidate sequences) and checks: exact apportionment, reconciliation, hash chains, no negative payable/liability, closed positions after liquidation, and `restore(snapshot)` equality.

## Accounting guarantees

* Exact money: largest-remainder apportionment, deterministic tie-break; parts always sum to the total (no "remainder to last partner").
* Partner positions are **derived** by folding append-only movements; reversals are negative movements, never edits.
* Movements and closes are SHA-256 hash chained; `verifyIntegrity()` and `Syirkah.restore()` re-verify; the DB trigger re-checks the chain on insert.
* Every movement maps to a balanced journal (`SyirkahPostingPlan`); journals are idempotent per `syirkah:<id>:movement:<seq>`. `PERIOD_RESULT` nets to each close's net result.
* `Syirkah.reconcile()` cross-checks the ledger: `sum(capital balance + payable − liability)` must equal net cash contributed plus the net results of effective closes (and liquidation). `verifyIntegrity()` includes it, so a lost, duplicated or forged movement is detected. `statement(partner)` gives a running-balance partner statement.
* Correction = reverse the latest close's movements + recompute. Allowed after profit distributions: payments beyond the corrected entitlement become a liability of that partner (`OVERPAYMENT_TO_LIABILITY`); refused after any other later movement (e.g. a liability settlement).
* The syirkah ledger records **partner capital/profit flows**. Operating revenue/costs are posted by the business's own ledger; the syirkah accounts reflect cash only for contributions, distributions and returns.

## Known limits (be aware before production)

* No JDBC/Panache repository yet: only the schema (`V300`, tables prefixed `syirkah_ledger_` so they coexist with the accounting module's own `syirkah_*` tables; verified on PostgreSQL 16 as a non-superuser) and the in-memory adapter. A repository maps `Syirkah.Snapshot` ⇄ tables; contracts/closes are stored as JSONB.
* Correcting an older (non-latest) close is intentionally unsupported; close the current period with an adjusting result instead.
* Liquidation treats all assets as already converted to cash (`LiquidationInput`); asset-by-asset sale/syuf'ah bookkeeping is out of scope.
* Mufawadhah conditions follow the Hanafi rule (equal capital and work); other madhhab variants would need a policy switch.

## Relation to the accounting module's syirkah front end

`numou-accounting-*` already provides contract versions with an approval lifecycle, business results, calculation
snapshots, a REST API and Postgres tables. This module is the **ledger behind it** (capital accounts, retained profit,
distribution, revision, correction, liquidation, journals). `adapter.bridge.BusinessResultBridge` is the seam: it maps a
`SyirkahBusinessResult` to a `ClosePeriod` command with deterministic UUIDs (syirkah, partner, close), so one business
result can only ever close one period and retries are safe. The ledger's own contract (capital, witnesses, ijab-qabul) is
still opened with `Commands.Open`; the front end's contract versions do not carry that data yet.

## PostgreSQL persistence

`JdbcSyirkahRepository` + `JdbcUnitOfWork` (package `adapter.persistence`, plain `java.sql`) persist the ledger into
`V300__syirkah.sql`:

- Contract versions, closes and the liquidation result are jsonb via `SyirkahJsonCodec` (lossless: amounts keep their
  scale); movements are rows. Only new rows are inserted; the one allowed update is a close going ACTIVE → REVERSED.
- `save` is optimistic (`UPDATE … WHERE version = expected`, which also takes the row lock). A failed save restores the
  aggregate's in-memory version.
- Every call sets `app.tenant_id` transaction-locally, so RLS applies; a foreign tenant finds nothing.
- `find` rebuilds the aggregate with `Syirkah.restore`, re-verifying every hash chain.
- The application clock is truncated to microseconds so timestamps (and the hashes over them) survive `timestamptz`.
- `JdbcSyirkahRepositoryIT` runs against a real database when `SYIRKAH_PG_URL`, `SYIRKAH_PG_USER` and
  `SYIRKAH_PG_PASSWORD` are set (role must not be superuser/BYPASSRLS).
`JdbcApprovalPort` (maker-checker requests; payload = the exact command, decided once, frozen by trigger) and
`JdbcOutboxEventPublisher` (events written in the ledger's transaction; `published_at` stamped once by an external
relay) use `V301__syirkah_governance_outbox.sql`. Note: V300/V301 sort *before* the date-style `V2026…` migrations
already in this repository; enable Flyway `outOfOrder` (or renumber) when applying to a database that already has them.

## Operations

- `SyirkahCatalogPort` / `SyirkahSummary`: keyset-paged listing (id order, optional status filter) for dashboards and
  APIs; PostgreSQL version in `JdbcOperationsSupport`, in-memory version on `InMemorySyirkahRepository`.
- `SyirkahIntegrityAuditor`: re-loads every syirkah of a tenant (re-verifying all hash chains and structural checks) and
  the equity identity; a ledger that cannot be restored is reported as a finding and never hides the others. Intended
  for a scheduled job.
- `OutboxRelay` + `OutboxPort`: at-least-once shipping of the transactional outbox. Rows are claimed with
  `FOR UPDATE SKIP LOCKED`, delivered to a sink, and marked published in the same transaction; a failing sink keeps
  order and resumes at the failed event. Consumers must de-duplicate on `eventId`; use one relay per tenant when strict
  order matters. The relay runs per tenant because the outbox is row-level-secured per tenant.
## Correcting an older close

`Syirkah.correctClose(target, replacementIds, …)` corrects any effective close, not only the latest. Every effective
close from the target onwards is reversed and replayed (the target with the corrected figures, later ones with their own
recorded results), because later allocations depend on the capital state the corrected close leaves behind: impairment is
repaired before profit exists, retained profit becomes capital. Distributions already paid stay as paid; whatever a
partner holds beyond his recomputed entitlement becomes an `OVERPAYMENT_TO_LIABILITY`. The correction is rehearsed on a
restored copy first, so a refused correction leaves the aggregate untouched. It is refused after an akad revision or
after a liability settlement. Through the application service, `Commands.CorrectClose.laterReplacements` carries the ids
for the replayed closes (empty = latest close, unchanged behaviour); the maker-checker payload stores them too.
