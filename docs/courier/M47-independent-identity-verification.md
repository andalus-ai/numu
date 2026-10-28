# M47 — Independent identity and Courier/Cargo verification

## Observed CI evidence

The M46 workflow change was exercised by GitHub Actions run 40607417356:
- Mobility verification completed with test errors in `WalletPostgresIntegrationTest` (Hibernate Reactive ordinal parameter labels).
- The identity bootstrap then ran the full identity reactor and failed inside `PostgresMembershipManagementRepositoryTest` before organization, quantity, supply-chain, or Courier/Cargo could execute.
- The identity test failure reported `IllegalStateException: No current Vertx context found` from synchronous `Panache.withTransaction(...).await().indefinitely()` in `seedRows()`.

This is a distinct issue from Mobility and still prevented Courier/Cargo verification. The log shows identity test setup is executing reactive Panache work without a Vert.x context. This milestone does not attempt to rewrite that test; it ensures its failure no longer suppresses downstream reactor reports.

## Change

- Bootstrap identity artifacts with `mvn -f identity/pom.xml install -DskipTests`. This still compiles the test sources but does not execute identity tests during prerequisite installation.
- Run organization, quantity, supply-chain, and Courier/Cargo verification using the installed identity artifacts.
- Run the identity reactor's full `verify` after downstream verification with `continue-on-error: true`.
- Propagate Mobility and identity verification failures at the end, so CI remains red when either capability fails while independent downstream test reports are still produced.

## Acceptance

- Confirm the bootstrap succeeds and organization, quantity, supply-chain, and Courier/Cargo steps execute.
- Inspect the Courier/Cargo Maven results independently; this change does not claim they pass.
- Confirm the final propagation step preserves the failed status when Mobility or identity tests fail.
- Track the reactive-context test fix separately; do not mask or disable identity tests.
