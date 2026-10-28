# M49 — Fix automotive-parts tenant-key normalization

## Evidence

Workflow run [40614069976](https://github.com/andalus-ai/numu/actions/runs/40614069976) failed before the Courier/Cargo reactor could run. The framework job reached `AutomotivePartsUseCasesTest.compatibleSearchFiltersNonMatchingAndCrossTenantClaimsAndGroupsEvidenceByProduct` and failed constructing a `PartFitment`: its two tenant IDs were supplied as `tenant-a`, but `VehicleFitment` uppercased its tenant ID to `TENANT-A`.

## Change and ownership

Tenant identifiers are opaque security-boundary keys, not human-readable catalog labels. `VehicleFitment` now trims and validates the tenant ID without changing its case. Market and vehicle catalog fields continue to normalize as before. The regression test verifies tenant-key preservation and catalog-field normalization; the existing compatibility-search test exercises construction and search with the same tenant.

This keeps the fix in the automotive-parts domain, where fitment consistency is owned. It does not weaken tenant checks or change persistence/API contracts intentionally.

## Acceptance

- Automotive-parts domain and application tests pass.
- CI proceeds past the automotive-parts gate and runs downstream document storage, security, mobility, supply-chain, and Courier/Cargo checks.
- Courier/Cargo results must be read from the resulting workflow; this change alone is not evidence that those tests pass.
- Independent Mobility wallet failure remains a separate known issue until verified otherwise.
