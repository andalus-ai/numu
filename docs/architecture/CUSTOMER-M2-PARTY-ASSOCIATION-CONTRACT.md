# Customer M2 — Customer-to-Party association contract

- **Status:** Domain and application contract introduced; persistence/runtime integration remains pending
- **Related:** [Customer Capability Contract](customer-capability-contract.md), [M1 implementation audit](CUSTOMER-M1-IMPLEMENTATION-AUDIT.md)
- **Implementation branch:** `customer/m2-party-association-contract`

## Decision

A CRM Customer is a tenant-owned customer role. Ecosystem Party is the canonical, globally addressable person or organization identity. The relationship is explicit and tenant-scoped; it is not inferred from names, email, phone, tax identifiers, or other profile attributes.

The association uses the existing strong identifiers:

- `CustomerId` identifies the tenant-owned customer role.
- `PartyId` identifies the canonical Ecosystem Party.
- The tenant is not supplied by the command or association value. Persistence must derive it from the trusted tenant execution context.

A Party may be referenced by customer roles in multiple tenants. A global Party lookup is identity validation only; it is not tenant authorization for the Customer.

## Code introduced

1. `CustomerPartyAssociation` — immutable domain value with typed CustomerId and PartyId plus association timestamp.
2. `CustomerPartyAssociationRepository` — public domain port. Implementations must scope both lookups and writes using trusted tenant context; no tenant ID is accepted from callers.
3. `AssociateCustomerWithPartyCommand` — explicit command to associate an existing Customer and Party.
4. `AssociateCustomerWithPartyHandler` — verifies the Customer is visible in the current tenant, verifies that Party exists and is active through Ecosystem's public `PartyLookupPort`, and persists the association through the tenant-scoped association port.
5. Repeating the same association is idempotent. Attempting to bind a Customer to a different Party is rejected; rebinding requires a separately designed audited workflow.

## Important constraints

- Do not infer Party kind from the current CRM Customer fields. The current Customer profile does not provide a reliable PERSON-versus-ORGANIZATION discriminator (a company may have individual contacts). A future API may accept a declared role/type only after the business semantics are settled.
- Do not auto-create Parties as part of CRM Customer creation.
- Do not backfill existing Customer rows automatically.
- Do not add a foreign key from CRM to Ecosystem storage; the capabilities may be independently deployed.
- Do not expose a Party's global existence through Customer endpoints. Customer lookup and authorization remain tenant-scoped.
- Do not impose a unique Party-per-tenant rule until the Account-versus-Customer and multiple-account semantics are decided.

## Remaining M2 integration work

This branch deliberately introduces the public domain/application contract first. It does **not** yet claim a complete deployable association feature. Before marking M2 complete:

- [ ] Implement `CustomerPartyAssociationRepository` in the CRM adapter with tenant-scoped SQL/JPA operations and unique association semantics.
- [ ] Add an additive migration for association storage, parent Customer ownership, indexes, and appropriate tenant-local constraints.
- [ ] Wire the handler into CRM composition and expose a versioned application/REST operation without changing existing Customer creation behavior.
- [ ] Add adapter tests proving two tenants can independently associate the same Party and cannot read or mutate one another's Customer associations.
- [ ] Add handler tests for unknown Customer, unknown Party, inactive Party, repeated same association, and conflicting rebind.
- [ ] Verify the modular-monolith and standalone composition paths and run the affected Maven reactors.

No migration, API exposure, or runtime adapter is included in this contract-only increment. No production-readiness claim is made.
