# Numou Syirkah implementation status

## Scope

The initial domain tranche was merged in PR #1. The hardening branch adds the first
application-service and PostgreSQL-adapter layer. This is still a draft implementation,
not approved for live accounting or payment processing.

## Implemented

### Domain

- Immutable effective-dated contract terms and partner terms.
- Exact 100% profit nisbah validation, duplicate party detection, currency checks, and contract-version checks.
- Net result calculation from revenue less direct, operating, and other costs.
- Deterministic largest-remainder allocation using exact integer arithmetic in currency minor units.
- Mudharabah ordinary-loss allocation to capital providers by capital risk.
- Musyarakah ordinary-loss allocation by contributed capital.
- Profit uses agreed nisbah for Mudharabah, Musyarakah, Inan, Abdan, Wujuh, and Mufawadah.
- Inan and Musyarakah ordinary losses are allocated by contributed capital; Mudharabah ordinary losses are allocated to capital providers only, with explicit zero allocations for non-capital partners.
- Abdan monetary loss, Wujuh loss (whose policy differs between its two models), and Mufawadah composite loss remain fail-closed until the contract records the exact approved structure/policy for that period.
- Calculation fingerprint and policy/version references.

### Application and persistence

- Application port and service for contract creation/activation, evidenced business-result registration,
  calculation persistence, maker-checker approval, and scoped reads.
- PostgreSQL adapter with transaction-scoped contract/result/calculation writes.
- Idempotent calculation persistence keyed by result and calculation hash.
- Distribution obligation rows created from immutable allocations.
- Immutable contract amendment workflow with draft, active, superseded, and rejected version states.
  Activation/rejection is maker-checker guarded, only the latest draft can activate, and only one open draft
  amendment is allowed at a time.
- Mufawadah is represented as a contract type; profit follows agreed nisbah, while loss calculation remains fail-closed because the source describes it as a composite of Inan, Abdan, Mudharabah, and Wujuh whose loss rules differ by component.
- Settlement repository method is idempotency-keyed, locks the obligation row, caps cumulative settlement,
  and refuses settlement unless the calculation is already marked POSTED.
- Audit-event rows for contract, result, calculation, approval, rejection, and settlement transitions.
- API skeleton uses `RequestContextHolder` and refuses requests without a verified tenant and actor.
- Evidence-backed loss responsibility aggregate separates reported loss, independent determination, maker-checker approval, journal reference, and reconciled recovery; ordinary loss and force majeure cannot be approved as partner recovery.
- Syirkah-aware close-cycle option adds mandatory result, allocation, and distribution tasks; an orchestrator gates task completion on persisted operation references and does not complete distribution until a posted journal reference is returned.
- Tests cover deterministic rounding, all-party allocations, profit nisbah across partnership types, responsibility approvals/settlement caps, and close-stage ordering.

## Source-to-rule traceability (initial, not exhaustive)

The rules below are traced to the uploaded `Hukum-muamalah.docx`, Chapter XV, sections on the named Syirkah forms. This is an implementation trace, not a fatwa or independent Sharia approval. The source's terminology and structure are retained; ambiguous mixed structures must not be inferred by the software.

| Source topic | Rule supported by the source | Current implementation | Remaining control / test |
|---|---|---|---|
| Syirkah Inan (Chapter XV, section C) | Profit follows agreement; ordinary loss follows each partner's capital proportion. | Positive net result uses agreed nisbah; Inan loss uses capital-contribution weights. | Add source-linked rule IDs to policy metadata and golden examples from the source's worked cases. |
| Syirkah Abdan (section D) | Partners contribute work without capital; profit share is by agreement and may be unequal. | Positive net result uses agreed nisbah. Monetary-loss calculation fails closed. | The source excerpt describes profit and work contributions but does not provide a general numeric monetary-loss allocation rule. Require approved policy before enabling such allocation. |
| Syirkah Mudharabah (section E) | Profit follows agreement; ordinary capital loss belongs to capital provider(s); manager bears loss caused by negligence/intent or breach of investor conditions. | Profit follows nisbah; ordinary loss is allocated to capital providers. Evidence-backed loss-responsibility workflow is separate from ordinary calculation. | Ensure evidence, independent determination, checker identity, approved liability basis, and recovery posting are durable and authorization-protected. |
| Syirkah Wujuh (section F), model I | The source treats the work-partner arrangement with an outside capital provider as substantively Mudharabah. | Current calculator represents Wujuh as one type for profit; Wujuh loss fails closed. | Contract must distinguish model I and model II. Model I should resolve to an approved Mudharabah-like policy only after explicit model metadata and review. |
| Syirkah Wujuh (section F), model II | Partners acquire goods on credit without their own capital contribution; profit follows agreement, while loss follows each partner's share of the goods owned. | Current calculator represents Wujuh as one type for profit; Wujuh loss fails closed. | Add goods-ownership proportions and model-specific golden tests before calculating loss. Capital-contribution weights are not a substitute for goods ownership. |
| Syirkah Mufawadah (section G) | The source describes a combination of Syirkah forms; the applicable profit/loss rules depend on the component forms present. | Profit follows agreed nisbah; monetary loss fails closed. | Model component agreements explicitly and calculate only when every component's policy is represented, approved, and tested. Do not treat a composite as a single generic capital-weight rule. |
| Net business result and break-even (worked contract examples) | Net profit is revenue less direct, operating, and other costs; break-even yields neither profit distribution nor loss allocation. | Business result computes these categories; calculator returns zero allocations for break-even. | Add golden tests matching source examples in IDR and document rounding assumptions separately from source rules. |

### Source interpretation boundary

The document supports the high-level rules above. It does not by itself specify production database isolation, tenant authorization, journal idempotency, payment settlement semantics, or a universal monetary-loss policy for every contract variant. Those are engineering controls or require an explicitly approved accounting/Sharia policy; they must not be represented as source-derived rulings.

## Known limitations — do not use for live financial transactions yet

1. **Authentication context is not wired end-to-end.** The resource requires `RequestContextHolder.tenantVerified()`
   and an actor, but the repository currently has no confirmed security filter/token integration populating them.
   Requests should fail closed until that integration is completed and tested.
2. **Company authorization is incomplete.** Tenant/company values are included in repository predicates, but a
   verified authorization check that the authenticated actor may operate on each company is still required.
3. **Journal posting is not integrated.** There is no production-safe posting gateway or atomic transaction joining
   the distribution state and the general ledger. The REST API intentionally does not expose settlement. The
   repository settlement method will reject obligations whose calculation is not marked POSTED.
4. **Contract lifecycle is partial.** Drafting, maker-checker activation/rejection, and version supersession are
   implemented. Suspension, termination/closure commands, effective-date overlap validation, and policy-governed
   migration of legacy contracts still need dedicated workflows.
5. **Database integration tests remain missing.** CI successfully applied the migration set through V13 on an earlier
   hardening commit, but the final code head still needs a fresh green run. PostgreSQL repository behavior,
   transaction rollback, locking, uniqueness, retries, and concurrent settlement need Testcontainers tests.
6. **API hardening remains.** Add bean validation, safe exception-to-HTTP mapping, rate limits, actor/correlation
   audit context, OpenAPI request/response schemas, and authorization tests.
7. **Existing Numou GL has architectural risks.** The current journal handler mutates account balances one by one
   before journal persistence, and uses default tenant/ledger references. Syirkah must not rely on this path for
   live posting until a single transactional, tenant-aware ledger posting boundary exists.
8. Run the Maven reactor and CI; review all results before merge.
9. Qualified Sharia advisers and accounting/legal professionals must approve supported structures, loss policies,
   evidence standards, and jurisdiction-specific rules.

## Additional close integration boundary

The close orchestrator expects a concrete SyirkahCloseOperations adapter to resolve the authoritative period result, persist the immutable calculation, allocate obligations, and return a confirmed posted journal reference. The current close aggregate is in-memory, so a crash between a domain operation and close-task completion is not atomic. Do not enable this workflow for live close until the adapter and durable close repository/transactional coordination are implemented.

## Accounting safety boundary

A calculated loss allocation is not automatically a partner receivable, expense, or journal entry.
Negligence, misconduct, breach, force majeure, and recoverability require evidence and an independent decision.
An approved calculation is still not a posted journal. A recorded settlement row is not proof that a bank transfer
or cash payment occurred; a payment rail/reconciliation integration is separately required.

## Build and validation

Run from the repository root:

```bash
./mvnw -pl Framework/finance/accounting/accounting-capabilities/numou-accounting-islamic -am test
./mvnw -pl Framework/finance/accounting/numou-accounting-adapter -am test
```

The full Maven reactor and PostgreSQL integration tests have **not** been executed by this branch authoring session.
