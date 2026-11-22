# Leaderboard Capability — M1 Domain Foundation

- **Status:** Implemented in feature branch; build/test execution pending
- **Date:** 2026-11-20
- **Branch:** `feat/leaderboard-m0-architecture-audit`
- **Depends on:** [M0 repository audit](LEADERBOARD-M0-REPOSITORY-AUDIT.md)

## Delivered

A new application-neutral capability is located under `core/framework/leaderboard` and is included in the `core/framework` Maven reactor. Its domain artifact is managed by the platform BOM.

### Domain model

- `LeaderboardId`: UUID-backed aggregate identity.
- `LeaderboardScope`: mandatory, nonblank tenant identifier; no implicit global/null scope.
- `ParticipantRef`: opaque subject kind and identifier; does not import identity, workforce, mobility or social entities.
- `ScorePolicyRef`: nonblank policy key and positive immutable version.
- `RankingDirection`: ascending or descending.
- `TiePolicy`: explicit competition, dense or ordinal tie semantics.
- `LeaderboardStatus`: draft, active, paused, completed and archived.
- `Leaderboard`: aggregate owning configuration and lifecycle transitions.

### Lifecycle rules

- New leaderboards start in DRAFT.
- DRAFT and PAUSED may transition to ACTIVE.
- ACTIVE may transition to PAUSED or COMPLETED.
- PAUSED may transition to COMPLETED.
- COMPLETED may transition to ARCHIVED.
- Illegal transitions fail with `IllegalStateException`.
- Activation and completion timestamps cannot precede creation/first activation respectively.
- The first activation timestamp is retained when a leaderboard is resumed.

### Dependency and packaging boundary

- `numou-leaderboard-domain` depends on the existing `numou-foundation` domain artifact and test-only libraries.
- No HTTP, CDI, Quarkus, persistence, broker, cache, Redis or consumer-domain dependency is introduced.
- The framework reactor includes the new `leaderboard` aggregator.
- The platform BOM manages `numou-leaderboard-domain`.

## Tests added

- Draft defaults and explicit ranking configuration.
- Valid lifecycle transitions.
- Invalid lifecycle transitions.
- Activation timestamp ordering and first-activation preservation.
- Completion timestamp ordering.
- Required tenant scope and positive policy version.
- Opaque participant reference validation.
- ArchUnit rule preventing infrastructure/framework dependencies from entering the domain package.

## Verification status

Source files and POMs were written to and fetched back from the feature branch successfully. The GitHub connector does not provide a local Maven execution environment in this workflow, and no PR-triggered workflow run exists for the branch commits. Therefore:

- **Source/branch verification:** passed.
- **Maven compile:** not run.
- **JUnit/ArchUnit execution:** not run.
- **CI:** a dedicated pull-request workflow was added; it has not executed because no PR has been opened.
- **Merge:** not performed.

Run from the framework reactor when a checkout/build environment is available:

```bash
cd core/framework
mvn -pl leaderboard/leaderboard-domain -am test
```

The command is intended to build the required upstream reactor modules and execute the leaderboard domain tests. Any existing repository-specific Maven profile or environment prerequisite must be applied as documented by the framework build.

## Deliberately deferred to M2+

Score contribution ledger, score entry idempotency, source event correlation, score adjustment/reversal, score-policy evaluation, ranking calculation, repository ports, persistence, snapshots, APIs, competitions, achievements, rewards and consumer adapters remain out of scope.

M1 does not claim the capability is production-ready. The next milestone must add score contribution semantics with deterministic numeric precision, immutable history and duplicate-delivery protection.
