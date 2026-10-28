# Person and Workforce M0 Implementation Audit

- **Status:** Evidence-based repository audit; implementation decisions remain subject to review.
- **Repository snapshot audited:** `main` at `58bcb5f1306f6da212bb25fada87e45ce1d6744f`.
- **Related contract:** [Person and Workforce Capability Contract](person-workforce-attribute-contract.md).
- **Purpose:** Identify existing reusable models and safety gaps before adding another identity or custom-attribute subsystem.

## 1. Findings from the current repository

### Canonical Party / person identity

- Ecosystem already defines `PartyKind.PERSON`, `PartyKind.ORGANIZATION`, and `PartyKind.UNKNOWN`.
- `PartyRef` references `PartyId` plus a `PartyKind`.
- Existing party relationship types include `CUSTOMER_OF`, `SUPPLIER_OF`, `PARTNER_OF`, `PROVIDER_OF`, `MEMBER_OF`, and `SUBSIDIARY_OF`.
- Foundation also has `PersonRef(UUID)`, described as a universal cross-domain reference to a person entity.
- In the inspected tree, the presence of these reference/value types does not by itself establish that a complete persisted Person profile aggregate and repository exist. Do not treat a reference as the person record or silently equate `PersonRef` and `PartyId` without an explicit compatibility contract.

### Workforce already exists

- `workforce/workforce-domain` contains a `Worker` aggregate with tenant ID, `PersonRef`, `WorkerType`, lifecycle status, and audit metadata.
- `Employment` references a `WorkerId` and an `OrganizationRef`, with employment type and start/end lifecycle.
- The workforce capability already has positions, assignments, skills, qualifications, attendance, compensation, payroll, compliance, and related models. Do not create a second HR/workforce capability.
- `RegisterWorkerCommand` accepts a raw UUID named `personId`; its handler converts it to `PersonRef`.
- **Important safety finding:** the inspected `RegisterWorkerHandler` generates a random `TenantId` instead of resolving a trusted tenant context. This is not a safe production tenant boundary and must be fixed as a separate, explicit workforce command/context change before using this handler as the pattern for Person integration.
- The in-memory `WorkerRepository` indexes workers by `PersonRef` alone, not by tenant. It is a test/development adapter, not proof of tenant-scoped uniqueness or isolation. Review production repository implementations and their uniqueness constraints before deciding whether multiple engagements per person are supported.

### Authentication identity is separate

- Identity `User` is explicitly documented as an authentication identity; it owns login email, password hash, display name, and account status.
- `UserEntity` persists to `identity_users`. The inspected model has no canonical Party/Person reference.
- Do not automatically create a Person for every user or require every Person to have a login. Define an explicit, optional, auditable association if the product needs to connect a user account to a Person.

### Existing attribute mechanisms are not yet a universal profile system

- Product specifications have typed `AttributeDefinition` and sealed `AttributeValue` types for product-specific attributes.
- Identity ABAC has `AttributeNamespace` and validated `AttributeReference` for authorization-policy attributes.
- Identity persistence also has federated-provider attributes stored as text.
- These systems have different semantics. Do not reuse product attributes for personal/workforce records or treat ABAC policy attributes as arbitrary business-profile metadata without verifying scope, tenant isolation, schema evolution, authorization, audit, and query behavior.
- No evidence in this audit establishes one existing attribute subsystem that satisfies all Person, Party, relationship, and workforce profile requirements.

## 2. Ownership and identity mapping

| Concern | Existing owner / evidence | Direction |
|---|---|---|
| Canonical party identity | Ecosystem `PartyId`, `PartyKind`, `PartyRef` | Remain canonical; inspect actual aggregate/repository/persistence before extending |
| Person cross-domain reference | Foundation `PersonRef` | Treat as a reference only; define mapping to canonical Party explicitly |
| Login/account | Identity `User`, `UserId`, `identity_users` | Keep authentication separate; optional explicit Person association |
| Workforce role | Workforce `Worker`, `WorkerId`, `Employment` | Reuse existing capability; do not add a second Worker/Employee identity |
| Employment relationship | Workforce `Employment` + `OrganizationRef` | Keep lifecycle and history in Workforce |
| Product attributes | Product specification attribute types | Product scope only unless a reviewed shared contract proves otherwise |
| Authorization attributes | Identity ABAC | Policy-evaluation scope, not a general profile store |
| Customer role | CRM Customer | Reuse canonical party references where supported; do not duplicate a person's identity |

## 3. Recommended implementation sequence

### M1 — Reconcile canonical identifiers and tenant context

1. Inspect the actual Ecosystem Party aggregate, repository ports/adapters, persistence, migrations, and callers.
2. Inspect the full Workforce repository contracts and all adapters/migrations; identify whether a production persistent worker repository exists and how tenant context is supplied.
3. Decide and document the explicit mapping between `PartyId` and `PersonRef`. Prefer a single canonical identity; introduce an adapter/translation only where compatibility requires it.
4. Replace random tenant creation in workforce command handling with a trusted tenant context/command contract. Reject missing tenant context; never infer tenancy from a person ID.
5. Decide whether a person may have multiple simultaneous worker/engagement records and define uniqueness per tenant/organization accordingly. Do not impose global uniqueness accidentally.
6. Add cross-tenant negative tests and tests for missing tenant context before introducing profile APIs.

### M2 — Minimal Person profile

1. Add a Person profile only if the repository audit confirms the canonical Party model lacks the required person-specific data and lifecycle.
2. Keep identity separate from role-specific data. Do not place job, salary, employer, attendance, driver/mechanic qualification, or assignment fields on Person.
3. Reuse Party contact/address associations and protect sensitive identity documents behind an explicit policy-controlled contract.
4. Use additive migrations and a reconciled, idempotent backfill; never merge records based only on name, email, phone, or document number.

### M3 — Governed custom attributes

1. Select a common attribute capability only after comparing the existing systems against required scopes and guarantees.
2. Require stable namespaced key, owning capability, subject scope, type/schema version, validation, tenant ownership, sensitivity classification, permissions, audit, and migration compatibility.
3. Keep invariant-bearing state (tenant ownership, authorization, verification, employment lifecycle, compensation) out of unrestricted custom values.
4. Begin with non-sensitive profile extensions; keep national identifiers, health, biometrics, and compensation out of generic free-form attributes.

### M4 — Integrate existing Workforce and consumers

1. Link Worker to the canonical Person/Party contract through an explicit public reference.
2. Preserve Worker, Employment, Position, and assignment history.
3. Integrate one audited consumer first, then expand to CRM, logistics driver/mechanic, consultants, and workspace membership only where real duplicate identity records are found.
4. Run the narrowest real Maven reactor tests and migrations; record exact commands and results.

## 4. Acceptance criteria before production rollout

- One documented canonical identity mapping; no parallel Person/Party identity source.
- Authentication account is optional and distinct from Person.
- Tenant context is explicit and trusted; no generated tenant IDs in application command handling.
- Repository uniqueness and reads are tenant/organization scoped according to approved engagement policy.
- Custom attributes are typed, scoped, versioned, permissioned, audited, and tested for cross-tenant isolation.
- Migrations have preflight/reconciliation, idempotency, rollback/forward-repair guidance, and preservation of historical snapshots.
- Tests cover duplicate registration, multi-engagement policy, missing tenant context, cross-tenant access, invalid attribute types, unknown keys, and schema evolution.
- Build/test status is reported from actual executions; this audit itself does not claim tests passed.

## 5. Explicit non-goals

This audit does not add a second identity engine, automatically link all Users to Persons, migrate customer/workforce records, collect additional sensitive data, or change production schema. Those changes require the M1 code and data audit above.
