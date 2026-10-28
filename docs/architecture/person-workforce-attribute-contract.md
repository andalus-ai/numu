# Person and Workforce Capability Contract

- **Status:** Proposed architecture contract
- **Scope:** Reusable person identity and domain-specific workforce/person profiles
- **Related:** [Party, Contact, Address, and Custom Attribute Capability Contract](contact-address-capability-contract.md), [Capability Packaging and Composition Contract](CAPABILITY-PACKAGING-AND-COMPOSITION.md), [Customer Capability Contract](customer-capability-contract.md)

## 1. Decision

A **Person is a kind of Party**, not a competing identity root. Numou should represent canonical identity in Ecosystem Party and expose a person-specific profile/contract where person semantics are needed. Organizations remain Party records too.

A **workforce member is not the same thing as a Person**. Workforce is a role/relationship between a Person and an employing or engaging organization, represented by a dedicated workforce capability when its lifecycle, policies, and records justify it. The same Person may be a customer, supplier contact, employee, contractor, marketplace seller, driver, mechanic, consultant, or workspace member in different contexts.

Do not create a separate person identity per business module. Use stable Party identity and explicit role/profile records with tenant-scoped authorization.

## 2. Conceptual model

```text
Party (canonical identity)
  ├── Person profile (when Party kind = person)
  └── Organization profile (when Party kind = organization)

Party relationships
  ├── person ↔ organization: employed-by / contractor-for / representative-of
  ├── person ↔ person: guardian / emergency-contact / related-party (where permitted)
  └── organization ↔ organization: subsidiary / branch / partner

Reusable associations
  ├── ContactPoint + PartyContactPoint
  └── Address + PartyAddress

Domain roles/profiles (separate lifecycle and policy)
  ├── Customer / CRM contact
  ├── Workforce member / contractor
  ├── Supplier representative
  ├── Driver / mechanic / technician
  ├── Marketplace seller
  └── Workspace member
```

This is a conceptual ownership model, not a claim that all listed roles or relationship types are implemented today.

## 3. Person profile boundary

The person profile may hold stable person-centric information not appropriate to every Party:

- legal name components and display/preferred name;
- name script/pronunciation or localized representation where needed;
- optional birth date or other regulated personal details only when a documented use case and legal basis require them;
- identity-document references through a protected identity-document contract, not as unrestricted custom strings;
- profile completeness/provenance and effective-dated changes where required.

Do not place employer, job title, salary, manager, work schedule, leave balance, license-to-operate a vehicle, or current assignment on the canonical Person. These change by role, organization, or time and belong to the relevant domain capability.

Avoid collecting sensitive personal data by default. Keep identity documents, national identifiers, biometric data, health information, and other highly sensitive fields out of generic free-form attributes. Require explicit purpose, access policy, retention rules, audit, and appropriate encryption/protection for any supported sensitive record.

## 4. Workforce is a separate capability

Workforce should own workforce lifecycle and policies, with references to canonical Person and Organization Party IDs. Depending on actual product scope, workforce may include:

- worker profile and engagement type (employee, contractor, temporary, volunteer, consultant);
- employing/engaging organization and effective-dated employment/contract relationship;
- job, position, grade, department, reporting line, and organizational assignment;
- work location, working arrangement, shift/availability and assignment history;
- skills, certifications, licenses, training and expiry dates;
- onboarding/offboarding, status, probation, and workforce eligibility;
- attendance, time tracking, leave, payroll and compensation only in their respective governed sub-capabilities or systems of record.

These are not all automatically part of a first release. Audit existing HR/workforce modules and identify their actual ownership before introducing or moving models. Payroll, compensation, health, and other sensitive data require stronger access controls and should not be added merely because a generic workforce profile exists.

The workforce relationship must be effective-dated and support rehire, multiple concurrent engagements where policy permits, transfers, and historical assignments without overwriting the person's identity. Employee numbers are scoped identifiers within an employer/tenant, not global Party IDs.

## 5. Custom attributes and extension profiles

Dynamic attributes are required for different person and workforce use cases, but use governed definitions and explicit scope. Avoid an unrestricted `Map<String, Object>` that bypasses invariants or permissions.

### 5.1 Supported scopes

Subject to audit of existing attribute infrastructure, allow attribute definitions to target:

- Party;
- Person profile;
- Organization profile;
- Party relationship;
- ContactPoint or Address;
- workforce member / engagement;
- job or position;
- assignment, qualification, or another explicitly registered extension scope.

A definition must declare its scope and cannot be applied to an unrelated record type just because both records happen to contain a Party ID.

### 5.2 Definition and validation

Each definition should have:

- a stable namespaced key and owning capability;
- target scope and optional profile/schema identifier;
- label, description, and localization;
- explicit data type, schema version, required status, default, and validation constraints;
- controlled vocabulary/options for choice values;
- sensitivity classification and read/write permissions;
- search/indexing policy and retention/deprecation state.

Use a typed value system (for example text, integer/decimal, boolean, date/time, single/multiple choice, and references) validated against the active definition version. Unknown keys and invalid values must be rejected or handled by an explicit compatibility policy, never silently accepted.

Illustrative keys:

- `person.pronunciation_guide` — optional person-profile extension;
- `workforce.safety_induction_date` — workforce-specific qualification/training data;
- `workforce.site_access_level` — restricted and policy-controlled workforce attribute;
- `logistics.driver_badge_code` — logistics-owned attribute on the appropriate driver/worker role, not on canonical Person by default.

These names are examples, not a mandate to add those fields to the core schema.

### 5.3 Reuse the existing attribute system first

Before adding an attribute engine, inventory existing product-specification, identity/ABAC, workspace/database-property, and other metadata systems. Reuse a system only if it supports the required target scopes, tenant isolation, typing/validation, schema evolution, sensitivity, authorization, audit, and query behavior. Authorization attributes and user-configurable business profile attributes have different threat models and should not be conflated without a clear boundary.

Keep invariant-bearing state—canonical IDs, tenant ownership, employment status transitions, verification, authorization decisions, consent, and payroll amounts—under explicit domain models and policies. Custom attributes must not bypass these rules.

### 5.4 Attribute profiles

Where a use case needs a coherent group of fields, define a versioned profile/schema such as `mechanic-profile`, `driver-profile`, `consultant-profile`, or `field-worker-profile`. A profile selects permitted attribute definitions and validation rules for its scope; it does not create a new identity record.

Profile changes need migration/compatibility semantics. Required fields should be evaluated at defined lifecycle points, rather than retroactively making every historical record invalid without a migration plan.

## 6. Tenant, organization, and authorization rules

- Party identity is canonical, but access to Party data is not automatically global. Apply explicit tenant-sharing/visibility rules and avoid cross-tenant existence leaks.
- Workforce records and employment relationships are tenant/organization scoped. The same person may participate in multiple tenants under distinct engagements and permissions.
- Distinguish global identity data from tenant-specific profile extensions and workforce records.
- A tenant may define additional attributes within its authorized scope, but cannot overwrite shared system definitions or read another tenant's private values.
- Enforce authorization at query and mutation boundaries, including exports, search, custom-attribute filtering, and history views.
- Audit access and changes for sensitive personal/workforce data; minimize sensitive values in logs and events.

## 7. Migration and compatibility

- Inventory existing Person, Employee, User, CustomerContact, Driver, Mechanic, Consultant, and HR records before migration; names are examples of possible legacy models, not a statement that all exist.
- Map existing records to canonical Party IDs using reviewed, idempotent, reconcilable migration steps.
- Do not merge identities based solely on matching names, phone numbers, email addresses, or national IDs.
- Preserve legacy APIs through adapters/read models where appropriate, and avoid a flag-day migration.
- Keep authentication principal/account identity distinct from Person profile. A person may have no login account, and a service/user account must not automatically be treated as a natural person.
- Keep transaction-time snapshots and historical employment/assignment records stable.

## 8. Milestones and acceptance criteria

### M0 — Audit
- Inspect repository instructions and architecture ADRs.
- Inventory current Party kinds, profile models, relationship types, custom-attribute systems, User/Identity models, HR/workforce modules, and relevant persistence/migrations.
- Produce a model/ownership matrix and identify duplicate person-like identities.
- Record current test/build commands and actual verification status.

### M1 — Person and relationship contract
- Define Party kind/person-profile semantics and organization representation.
- Define effective-dated relationship types and public references.
- Clarify identity-document handling and data-minimization requirements.
- Add architecture tests for dependency direction and identity ownership where supported.

### M2 — Attribute governance
- Select/reuse an existing attribute system only after evidence-based comparison.
- Implement scoped typed definitions, validation, versioning, permissions, and audit only for the requirements not already met.
- Add tests for invalid type, unknown key, tenant isolation, schema evolution, and restricted attributes.

### M3 — Workforce integration
- Define Workforce ownership and Party/Person/Organization references.
- Integrate the first real workforce consumer only after its existing model and lifecycle are audited.
- Preserve employment/assignment history and compatibility.

### M4 — Production hardening
- Verify authorization, privacy/retention, audit, query/index behavior, migration reconciliation, observability, and standalone/modular-monolith composition.
- Run affected tests and report exact commands/results; do not claim unrun tests passed.

## 9. Pull request policy

Deliver milestone-sized PRs, not one PR per small change. Do not merge without explicit authorization. No destructive migration, automatic identity merge, or broad sensitive-data collection is authorized by this contract.
