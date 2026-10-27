# M46 — Independent capability verification in CI

## Problem

The combined framework/logistics CI job runs several independent Maven reactors in sequence. A failure in the Mobility reactor previously stopped the job before identity, organization, quantity, supply-chain, and Courier/Cargo verification could run. This made a Mobility failure obscure whether the Shipment/Courier contract itself remained buildable.

The M44 merge run illustrates this: Mobility wallet integration test startup failed while creating the Quarkus test application because a stale/incompatible `SecurityConfig` config-mapping bean was referenced. The failure was in the Mobility API test setup, not in a Shipment test; downstream Courier/Cargo verification was skipped. A later source change replaced the mapping with runtime-injected `@ConfigProperty` fields, but the main CI run that included the subsequent wallet-test change was cancelled by a newer main push before the full reactor result was available.

## Change

The Mobility verification step now has an explicit step ID and `continue-on-error: true`. This records the failure as the step's `outcome` while allowing independent downstream reactors to run. After Courier/Cargo verification, a final conditional step propagates the Mobility failure so the overall job still fails when Mobility is red. The Maven test report upload remains unconditional.

This is intentionally not a blanket `continue-on-error`: the final status remains failed if Mobility fails, while independent reactor evidence is no longer suppressed by that earlier failure.

## Verification limits

- The workflow change is subject to GitHub Actions validation on this PR.
- No claim is made that the current Mobility or Courier/Cargo reactors pass.
- The latest main run before this change was cancelled during framework installation because newer commits superseded it.
- This does not replace fixing a failing reactor; it improves diagnostic coverage so separate capabilities report their results independently.
