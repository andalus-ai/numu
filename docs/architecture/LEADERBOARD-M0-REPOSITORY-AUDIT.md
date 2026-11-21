# Leaderboard Capability — M0 Repository Audit and Architecture Foundation

- **Status:** M0 audit complete; implementation not started
- **Date:** 2026-11-20
- **Repository:** `andalus-ai/numu`
- **Target:** reusable, application-neutral Leaderboard capability
- **Related contracts:** `AGENTS.md`, ADR-003, Capability Packaging and Composition Contract, Domain Capability Matrix

## 1. Objective

Establish a source-backed ownership and integration boundary for reusable leaderboards before adding domain code, persistence, APIs, or consumer dependencies.

Leaderboard provides configurable ranking and competition primitives. It does not own the business facts used to calculate scores, canonical participant identity, authorization policy, financial rewards, or source-domain lifecycle.

## 2. Repository findings

The following were inspected on the current `main` branch:

| Area | Observation | Consequence |
|---|---|---|
| Contributor rules | Root `AGENTS.md` requires ownership-first design, reactor inspection, explicit contracts, tenant/security controls, and milestone-sized changes | Follow existing capability contracts; do not create speculative scaffolding |
| Repository layout | Repository has multiple Maven reactor roots; ADR-003 explicitly states there is no repository-root Maven project | Do not add a root-level Maven module or assume `mvn test` from repository root |
| Shared framework | `core/framework/pom.xml` is a Java 21 framework reactor with Foundation, event, reliability, cache, runtime, workflow, social-graph and other modules | Candidate composition infrastructure exists, but this does not by itself establish leaderboard ownership |
| Existing capability ownership | Domain Capability Matrix assigns explicit ownership and defers several platform capabilities; its initial extraction wave is intentionally constrained | Keep this implementation in the monorepo; do not create a new repository or move code as part of M0 |
| Existing leaderboard implementation | No `leaderboard/` or `gamification/` top-level directory was found; repository code search for `leaderboard`, `gamification points badges achievement`, `score points rank ranking`, and `competition tournament challenge` returned no matches | Treat Leaderboard as a new capability; do not claim reuse of an existing implementation |
| Event/reliability infrastructure | Framework reactor includes event and reliability modules; concrete contracts must be inspected before wiring | M0 defines integration requirements only; M1/M2 must use verified public contracts |

The repository contains existing social-graph, feedback, survey, attendee, affiliate, commerce, workforce and other capabilities. Their existence does not transfer ownership of generic ranking to those domains.

## 3. Ownership decision

Create a distinct **Leaderboard capability** in the Numou monorepo, with its own domain and application boundary.

The exact Maven reactor placement and artifact split are an M1 entry gate: inspect the current framework module conventions, parent/BOM management, event contracts, tenant context and persistence patterns before adding POM entries. Do not place leaderboard inside POS, CRM, Workforce, Marketplace, Mobility, Social Graph, Feedback or Finance.

### Leaderboard owns

- Leaderboard definition and lifecycle (draft, active, paused, completed, archived).
- Participant references and participation state for a particular leaderboard.
- Ranking configuration, ordering, tie policy and visibility policy.
- Score contribution records, score adjustments and score-ledger invariants.
- Ranking calculations and query semantics.
- Period windows and snapshot semantics when implemented.
- Competition lifecycle only where it is explicitly a leaderboard-owned concept.

### Other capabilities retain ownership

- Identity owns canonical people and organization identities.
- Tenancy owns tenant lifecycle and tenant context; Security owns authentication and authorization policy.
- Source capabilities own source facts and eligibility: POS owns sales facts, Mobility owns trip/driver operational facts, Workforce owns workforce facts, Commerce owns marketplace/order facts, Social Graph owns social relationships, and so on.
- Finance owns monetary rewards, wallets, payouts and accounting.
- Notification owns delivery of ranking/achievement notifications.
- Achievement/reward catalogs are not assumed to be Leaderboard-owned; their boundary will be decided separately when M5/M6 scope is designed.

## 4. Domain-neutral contract

A leaderboard must be usable without importing any consumer capability.

### Core concepts

| Concept | Responsibility |
|---|---|
| Leaderboard | Aggregate defining purpose, subject kind, scope, score policy reference, ranking direction, lifecycle and visibility |
| ParticipantRef | Opaque, stable subject reference supplied by the owning identity/domain contract; not a copied Person/Employee/Driver entity |
| ScoreEntry | Immutable contribution or adjustment with source reference, idempotency key, effective time, recorded time and audit context |
| ScorePolicyRef | Versioned reference to a scoring policy; policy evaluation must be deterministic and reproducible |
| RankingEntry | Derived rank, score and participant reference for a leaderboard and period |
| RankingSnapshot | Rebuildable, time-stamped materialization of a ranking result |
| Competition | Optional bounded-time contest composed through leaderboard rules; not required for a permanent leaderboard |

### Required invariants

1. Every write is scoped by an explicit tenant or explicitly declared public/system scope. Missing scope is never treated as global.
2. A participant is referenced by stable ID and subject kind; leaderboard does not become an identity registry.
3. Score contribution writes are idempotent within their declared tenant and leaderboard scope.
4. Accepted score entries are append-only. Corrections use compensating entries or explicitly audited reversals; no silent overwrite.
5. Replaying the same source event cannot add the same contribution twice.
6. Ranking is deterministic for a fixed input set and policy version.
7. Tie semantics are explicit and versioned (for example, competition rank versus dense rank); unspecified tie behavior is not allowed.
8. Effective time and recorded time are distinct so late-arriving events and historical rebuilds are explainable.
9. Ranking visibility and participant visibility are authorized independently; unlisted/private participants must not leak through public ranking queries.
10. A score is not currency, a wallet balance, an employment rating, a safety determination or a payout instruction.

## 5. Integration contract

- Consumers submit validated facts or versioned score-contribution commands through a public application contract or a versioned event contract.
- Source event identity, tenant scope, source capability, actor/correlation context and policy version are retained where available.
- Consumer adapters translate source-domain facts into neutral contribution commands; the Leaderboard domain must not import consumer entities or persistence models.
- Event consumption must use durable idempotency/inbox semantics. Use an outbox when the source capability requires atomic publication with its own state transition.
- Leaderboard publishes versioned lifecycle and ranking-change events only after its own state is committed.
- Cross-capability rewards are requests to the owning capability and are never issued by mutating a score.
- No direct cross-capability database reads or writes.

M0 does not select a broker, cache, database adapter, HTTP framework or distributed deployment topology. PostgreSQL is the expected first durable store only after confirming the framework's current migration and transaction conventions. Redis, if later used, is a rebuildable read cache and never the score authority.

## 6. Proposed artifact shape (subject to M1 validation)

Use the smallest artifact set that matches the existing framework reactor and build conventions:

- Domain: aggregates, value objects, policies and domain events; pure Java.
- Application: commands, queries, handlers, authorization/context ports, repository ports and transaction coordination.
- SPI/public contract: stable consumer-facing types only if a separate contract artifact is justified by real consumers.
- Persistence adapter: score ledger, leaderboard definitions, idempotency and snapshots.
- Transport adapter: versioned REST/event adapters only when real host/runtime requirements are established.
- Runtime composition: optional wiring, never a second owner of domain rules.

Do not add a generic cross-domain scoring engine that evaluates arbitrary executable formulas. Initial policies should be constrained, typed and deterministic; untrusted scripts and user-supplied expressions are out of scope.

## 7. M0 acceptance evidence

- [x] Root contributor guidance inspected.
- [x] ADR-003 and capability packaging contract inspected.
- [x] Domain Capability Matrix inspected.
- [x] Framework reactor and Java version inspected.
- [x] Repository code search performed for leaderboard/gamification/ranking concepts.
- [x] Ownership and non-ownership boundaries documented.
- [x] Tenant, idempotency, audit, replay and deterministic ranking requirements documented.
- [ ] Existing tenant-context and authorization contracts traced to exact public APIs.
- [ ] Existing event/outbox/inbox contracts traced to exact public APIs.
- [ ] Existing persistence/migration conventions traced in the selected reactor.
- [ ] M1 artifact placement and module graph approved by source inspection.
- [ ] M1 implementation and tests.

M0 is an architecture and repository audit, not a claim of working software. No Java source, Maven reactor, database schema, API or runtime behavior has been added in this milestone.

## 8. M1 entry gate

Before writing domain code:

1. Inspect the exact tenant context and actor/correlation types available in the framework.
2. Inspect event envelope, event publisher, outbox/inbox and idempotency APIs; reuse only documented public contracts.
3. Inspect representative capability POMs and module source/test layout to select artifact placement and dependencies.
4. Define aggregate boundaries and typed identifiers with no infrastructure dependencies.
5. Add domain tests for lifecycle, scope, immutable score entry, duplicate contribution and deterministic tie behavior.
6. Update the appropriate reactor POM/BOM only after the module graph is verified.

## 9. Explicitly deferred

Scoring formulas and weights, score ledger persistence, ranking queries, snapshots, REST APIs, real-time push, competitions, achievements, badges, rewards, anti-abuse signals, analytics, Redis caching, and consumer integrations are later milestones. No consumer module is modified in M0.
