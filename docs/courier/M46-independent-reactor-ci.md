# M46 — Independent Courier/Cargo verification in CI

## Problem

The repository CI workflow deliberately allows the Mobility verification step to fail and intends to propagate that failure only after downstream identity, organization, quantity, supply-chain, and Courier/Cargo reactors have run. The latest observed run (40450414652) contradicted that intent: `mobility_verify` failed in `WalletPostgresIntegrationTest.concurrentRequestsWithTheSameReferenceProduceOneMovement`, and the downstream steps were skipped.

The reported root cause is Quarkus configuration validation for `tech.kayys.numou.security.adapter.SecurityConfig`:
`SRCFG00027: Could not find a mapping for tech.kayys.numou.security.adapter.SecurityConfig`.

That is a Mobility/security test configuration defect. It does not demonstrate a Courier failure, because the Courier reactor did not execute.

## Change

The first downstream step, installing identity contracts, now has an explicit condition that allows it to run when the isolated Mobility step has completed (including with failure), but not when that step was cancelled or skipped.

This preserves the intended gate order:
1. Mobility verification records its outcome without immediately stopping the job.
2. Identity contracts are attempted despite an isolated Mobility test failure.
3. Existing normal success gating keeps organization, quantity, supply-chain, and Courier/Cargo from running if an actual prerequisite installation fails.
4. The existing final propagation step still marks the overall job failed when Mobility verification failed.

## Acceptance evidence still required

- GitHub Actions must confirm that identity, organization, quantity, supply-chain, and Courier/Cargo steps execute after the known Mobility failure.
- The Courier/Cargo Maven reactor must finish and publish its Surefire reports.
- The overall workflow is expected to remain failed while the Mobility blocker exists; this change is isolation, not a fix for the Mobility configuration defect.
- Do not treat a green module-topology job as evidence that Courier tests passed.

## Scope

This change improves verification isolation only. It does not modify Courier domain behavior, persistence, migrations, booking idempotency, or transport/authentication contracts. No Maven build was run locally during this GitHub-only edit.
