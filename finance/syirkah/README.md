# Numou Syirkah — Islamic partnership accounting

Implements syirkah accounting per *hukum muamalah* on top of numou (DDD + hexagonal, pure-Java domain).

| Module | Contents |
|---|---|
| `numou-syirkah-domain` | `Syirkah` aggregate, `SyirkahContract` (versioned akad + fiqh validation), `ProfitLossAllocator`, `Apportionment`, append-only `PartnerMovement`s, hashed `CloseSnapshot`s, liquidation, `SyirkahPostingPlan` |
| `numou-syirkah-application` | `SyirkahApplicationService`, commands, ports (`SyirkahRepository`, `JournalPostingPort`, `EventPublisherPort`, `TransactionPort`) |
| `numou-syirkah-adapter` | in-memory reference adapters, `JournalEntryPostingAdapter` (posts to numou `JournalEntry`), PostgreSQL `V20261023_01__syirkah.sql` |

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

## Akad formation (Hukum Muamalah Bab X, XI, XVIII)

`AkadFormationChecker` checks that an akad was validly formed and properly written, from an `AkadFormation` record kept apart from `SyirkahContract` (so the contract's canonical form and stored hashes do not change) and a `FormationPolicy` for the choices the book leaves open (witness rule, multi-akad, optional minimum age). Each `FormationFinding` carries a code, a book reference and an `AkadDefect`: `BATIL` (defect in a rukun or one of the seven in'iqad conditions), `FASAD` (unclear terms, coercion), `INCOMPLETE` (missing party data, ijab and qabul wording, sections or witnesses, which the book lists as deviations without calling them batil) or `UNCLASSIFIED` (contract violations the book does not classify here, awaiting the Sharia adviser). Software cannot decide lawfulness or sound mind: it requires the determination to be recorded and blocks a negative one. `SyirkahApplicationService.openWithFormation` refuses to open on any finding; plain `open` skips the checks by default; a service built with `FormationEnforcement.REQUIRED` refuses plain `open`, so `openWithFormation` is the only way in (roadmap gate B for a deployment that turns it on; the REST API does not yet carry the formation data, so it must stay on the default until it does). The formation record and its policy are stored with the syirkah (`AkadFormationPort`, table `syirkah_ledger_akad_formation`, append-only). `POST` on the REST API takes an optional `formation` and `formationPolicy`; set `numou.syirkah.formation-enforcement=REQUIRED` to refuse requests without one (default `OFF`).

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

* No JDBC/Panache repository yet: only the schema (`V20261023_01`, tables prefixed `syirkah_ledger_` so they coexist with the accounting module's own `syirkah_*` tables; verified on PostgreSQL 16 as a non-superuser) and the in-memory adapter. A repository maps `Syirkah.Snapshot` ⇄ tables; contracts/closes are stored as JSONB.
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
`V20261023_01__syirkah.sql`:

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
relay) use `V20261023_02__syirkah_governance_outbox.sql`. The migrations use the date-style versions (`V20261023_01`, `_02`) so they sort after the other modules'
migrations; if you ship them later than a newer migration, enable Flyway `outOfOrder`.

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

## Asset-by-asset liquidation

`LiquidationInput.fromSales(sales, debts, date)` records each asset sale (`AssetSale`: description, proceeds, buyer
PARTNER/OUTSIDER, hak syuf'ah offered). Syuf'ah is therefore checked per asset: a sale to an outsider without a prior
offer makes `shufahOffered` false and blocks the liquidation; a sale to a partner is that offer taken up and the buyer
must be a partner of the current akad. Proceeds and the syuf'ah flag are derived from the sales and cross-checked by the
constructor, so they cannot disagree. Results with a breakdown use the `liquidation-v3` audit fingerprint (the sales are
tamper-evident); results without one keep `liquidation-v2` and an unchanged JSON shape, so existing data is untouched.

## REST API (`numou-syirkah-rest`)

Base path `/api/v1/syirkah`. Tenant and actor are taken **only** from the verified `RequestContextHolder`
(a header-supplied, unverified tenant is rejected with 403); request bodies never carry them.

| Method & path | Role | Purpose |
|---|---|---|
| `POST /` | operator | open a syirkah (optional client `syirkahId` makes a retry fail loudly instead of duplicating) |
| `GET /`, `GET /{id}` | any | cursor-paged list (`status`, `cursor`, `limit` ≤ 200); detail with positions |
| `POST /{id}/closes`, `GET /{id}/closes` | operator / any | close a period; list closes |
| `POST /{id}/distributions`, `/settlements` | operator | pay out profit; settle a partner liability |
| `POST /{id}/statements/balance-sheet`, `GET /{id}/closes/{closeId}/income-statement` | any | neraca (assets and outside debts are read from the syirkah's own ledger when configured and none are sent; otherwise the caller supplies them) and laba rugi in the book's layout |
| `GET /{id}/statements/capital-changes?from=&to=` | any | statement of changes in capital per partner for whole days (UTC) |
| `GET /{id}/closes/{closeId}/trading` | any | the trading figures a close was worked out from (kept when closing from trading figures) |
| `GET /{id}/lifecycle` | any | the records behind each lifecycle change, oldest akad version first |
| `POST /{id}/staff`, `GET /{id}/staff` | operator / any | engage staff (stored only if the Bab XVII check passes: fixed wage, employer is the syirkah) and list them |
| `GET /rules`, `/rules.md` | any | the Sharia rule catalog as JSON or Markdown |
| `GET /{id}/reconciliation`, `/integrity`, `GET /audit` | any / approver+auditor | equity identity, hash chain, tenant-wide audit |
| `GET /{id}/partners/{p}/statement.csv`, `/closes/{c}/report.csv` | any | CSV exports |
| `POST /{id}/revisions`, `/corrections`, `/liquidation/start`, `/liquidation` | operator | **governed**: returns `202` with a pending approval |
| `GET /approvals`, `POST /approvals/{id}/approve`, `/reject` | approver | decide; maker ≠ checker is enforced in the domain service |

Roles (`syirkah-operator`, `syirkah-approver`, `syirkah-auditor`) use `@RolesAllowed`; the deployment needs
`quarkus-security` with an identity provider (without one the endpoints stay closed, which is the safe default).
Errors: rule violation 422, not found 404, conflict / stale version 409, bad input 400, no verified tenant 403,
anything else 500 with a reference id and no internals.

The contract is published as `META-INF/openapi.yaml` (served by quarkus-smallrye-openapi if present; usable to generate a client).

`SyirkahProducers` wires the JDBC adapters. A deployment must supply its own `JournalPostingPort` and
`SyirkahAccountMap` beans, so a missing ledger gateway fails at start-up rather than silently.

## Lifecycle changes (new capital, replacement, death)

`LifecycleChecker` (Hukum Muamalah Bab X, XVI, XXIV, XXV) checks a change before `Syirkah.reviseContract` runs:
recorded consent with evidence from every partner (dissent is kept and blocks), closed books, a new akad version, the
profit decision, hak syuf'ah answers when the buyer is an outsider, and for a death the heir's decision. A partner's
death ends the akad; the heir decides whether to take the deceased's place. If the heir continues, the heir is a
partner in a new akad version that starts after the books are closed up to the date of death; if not, the share is taken
out. `SyirkahApplicationService.reviseWithLifecycle` runs the checks and the revision in one transaction and, when a
`LifecycleRecordPort` is configured, stores the record (table `syirkah_ledger_lifecycle`, V20261029_01, append-only,
tenant RLS), one row per akad version.

Limit: the heir's inherited capital is not transferred as a single movement. The ledger returns the deceased's capital
(payable to the estate) and records the heir's contribution, so the cash side must be settled outside this module.

## Staff contracts (ijarah)

`StaffContractChecker` (Hukum Muamalah Bab XIV principle 10, XVI section D, XVII) checks a staff contract against the
syirkah's akad: the employer must be the syirkah and not one partner acting for himself (batil); a partner who manages
or works in the syirkah cannot be salaried; the kind of work, period and effort must be stated; the wage must be a
fixed nominal amount in the syirkah's currency (a share of sales or profit is rejected as unclear); extra (ja'li)
clauses need a recorded lawfulness review. It is a pure check: staff contracts are not yet stored or exposed over REST.
## Agriculture

`AgricultureChecker` (Hukum Muamalah Bab XV section H) keeps muzara'ah and mukhabarah out: both are haram, so the
akad formation check rejects an akad whose stated activity or combined akad names either (batil, as an akad forbidden
by the texts). A musaqah, which is allowed only on land that already has its trees, can be checked on its own. Numou
has no ledger type for these arrangements; the name scan is a safeguard, not a substitute for the lawfulness review.
