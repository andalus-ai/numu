# Advertising Platform — M1 Host Integration

- **Milestone:** M1 integration hardening
- **Branch:** `feat/advertising-m1-host-integration`
- **Status:** Code and a dedicated CI workflow are committed on a feature branch. Execution of the new workflow is pending.
- **Builds on:** merged advertiser/publisher/inventory and campaign/creative capability modules.

## Changes

### Trusted authorization adapter

`AdvertisingAuthorizationAdapter` implements `AdvertisingAuthorizationPort` by composing existing platform contracts:

- `CurrentSecurity` supplies the request's verified principal and resolved tenant. No actor or tenant is accepted from an Advertising request body.
- The adapter requires the requested Advertising tenant UUID to equal the verified request tenant.
- It requires the verified principal to be a member of that tenant.
- `MembershipManagementPort` rechecks that the user membership is currently active in Identity.
- `AuthorizationServicePort` evaluates effective permissions through the Security policy boundary. In the host composition, Identity's existing `IdentityPolicyAdapter` supplies those permissions.
- Missing context, malformed tenant identifiers, unknown action names, tenant mismatch, and inactive membership fail closed.

Advertising action names are mapped explicitly to the shared Security vocabulary:

| Advertising action | Security resource | Security action / permission |
|---|---|---|
| `advertiser.register` | `advertiser` | `create` |
| `advertiser.activate` | `advertiser` | `perform` |
| `publisher.register` | `publisher` | `create` |
| `publisher.activate` | `publisher` | `perform` |
| `publisher.inventory.manage` | `publisher-inventory` | `update` |
| `publisher.inventory.verify` | `publisher-inventory` | `perform` |
| `campaign.create` | `campaign` | `create` |
| `campaign.manage` | `campaign` | `update` |
| `creative.create` | `creative` | `create` |
| `creative.submit` | `creative` | `update` |
| `creative.review` | `creative` | `perform` |
| `creative.manage` | `creative` | `update` |

The resulting effective permission codes are therefore `advertiser.create`, `advertiser.perform`, `publisher.create`, `publisher.perform`, `publisher-inventory.update`, `publisher-inventory.perform`, `campaign.create`, `campaign.update`, `creative.create`, `creative.update` and `creative.perform`. These are normal Identity-managed permissions; Advertising does not introduce a second role or permission store.

### Canonical Party verification

A new application port, `AdvertisingPartyVerificationPort`, requires advertiser and publisher registration to resolve to an active canonical Party.

`EcosystemPartyVerificationAdapter`:
- resolves the opaque reference through the public `PartyLookupPort`;
- accepts the canonical Party UUID representation;
- returns false for malformed, missing or inactive Party references;
- does not copy Party data or introduce an Advertising-owned Party table.

The account service performs authorization first and then verifies the Party before persisting an advertiser or publisher account. Existing tenant-scoped account persistence remains unchanged.

### Module and CI wiring

- Added `numou-advertising-host-adapter` to the Advertising reactor.
- The adapter depends on public Identity, Security and Ecosystem contracts; it does not depend on Identity's PostgreSQL implementation.
- Added `.github/workflows/advertising-capability.yml`, which installs the required cross-reactor contracts and verifies the complete Advertising reactor, including PostgreSQL Testcontainers tests.

A runtime composition must include the Identity adapter (to provide effective permissions and membership), Security adapter (verified request context), and Ecosystem persistence adapter (canonical Party lookup). The Advertising host adapter remains replaceable and does not own any of those persistence stores.

## Tests added

- Authorization adapter: verified tenant, active membership, explicit action mapping, tenant mismatch, unknown action and inactive membership.
- Party adapter: active/inactive Party resolution, malformed reference and missing Party.
- Advertising application: registration is blocked when canonical Party verification returns false.

## Verification status

No Maven execution is claimed from the authoring environment. The dedicated GitHub Actions workflow is committed but has not yet produced a run on this branch. The merged M1/M2 PR checks did not include a dedicated Advertising reactor job, so their unrelated capability results are not evidence that Advertising itself compiled or passed its PostgreSQL tests.

## Remaining boundary

This integration closes the account-registration and authorization-port gap. It does not add REST endpoints or a runtime composition root; those belong in the next host/API integration step. The host must derive the Advertising tenant from `CurrentSecurity`, never trust caller-supplied tenant or actor values, and dispatch the blocking Advertising JDBC adapter on a worker thread.
