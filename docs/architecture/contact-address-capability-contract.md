# Party, Contact, Address, and Custom Attribute Capability Contract

- **Status:** Proposed implementation contract; repository audit is partial and runtime behavior remains to be verified
- **Scope:** Reusable identity, communication endpoint, address, and extensibility contracts for Numou
- **Related:** [ADR-003 — Reusable capability modules](ADR-003-reusable-capability-modules.md), [Capability Packaging and Composition Contract](CAPABILITY-PACKAGING-AND-COMPOSITION.md), [Customer Capability Contract](customer-capability-contract.md)

## 1. Decision

Numou should expose reusable Party, Contact, and Address capabilities through explicit public contracts. This work must evolve the existing Ecosystem implementation rather than introduce a competing identity/contact/address source of truth.

A capability is a business boundary, not automatically a new Maven reactor, database, service, or deployment. Keep the current deployment-neutral domain model and add adapters or artifacts only when the actual dependency and composition requirements justify them.

This document describes the target contract; it does not assert that the current repository already satisfies every requirement.

## 2. Initial repository findings

The initial source audit on `main` found existing building blocks under `core/framework/ecosystem`:

- `PartyId`, `PartyRef`, and party relationship types in the Ecosystem domain.
- `Address`, `PostalAddress`, `AddressId`, and `AddressKind`.
- `ContactPoint`, `ContactPointId`, contact-point kinds/status, and verification status.
- `PartyAddress` and `PartyContactPoint` association records with purpose, primary flag, and effective period.
- Application ports `AddressRepository` and `ContactPointRepository`.
- Domain tests covering postal normalization, country-code shape, contact-point verification reset after changing a value, effective periods, and Party associations.
- CRM's `Customer` aggregate still contains legacy direct fields for email, phone, address, city, state, postal code and country, plus nested customer contact/address collections.

The audit did not establish the full persistence/runtime implementation, all consumers, or tenant/security guarantees. These must be verified before changing storage or migrating callers.

## 3. Ownership boundaries

| Concern | Authoritative owner | Rule |
|---|---|---|
| Person or organization identity | Ecosystem Party | One canonical identity; stable opaque ID |
| Generic Party relationships | Ecosystem Party | Model relationships without embedding CRM or industry workflow |
| Communication endpoint | Existing Ecosystem ContactPoint model, subject to contract review | Email, phone, messaging endpoint and future channels; value changes reset verification |
| Association of endpoint to Party | Existing PartyContactPoint model, subject to contract review | Purpose, primary selection, and effective period belong to the association |
| Postal/physical location | Existing Ecosystem Address model, subject to contract review | Address is distinct from the party that uses it |
| Association of address to Party | Existing PartyAddress model, subject to contract review | Purpose, primary selection, and effective period belong to the association |
| Customer role and customer-account lifecycle | Customer capability | A customer is a Party role in a tenant/business context, not a second identity |
| Leads, opportunities, pipelines, sales activities | CRM | CRM consumes Party/Customer contracts; it does not own canonical contact details |
| Consent and communication preferences | Explicit privacy/consent owner, to be confirmed during audit | Do not duplicate consent state across CRM, Customer, and Contact |
| Order/invoice/shipment recipient at transaction time | Owning transaction capability | Preserve a transaction-time snapshot where legal or operational history requires it |
| Custom attribute definitions and values | Reuse an existing shared attribute contract if it meets the requirements; ownership is an audit gate | Do not add a second generic attribute engine without evidence that existing mechanisms cannot serve this need |

## 4. Public contract requirements

- Use stable Party, Address, and ContactPoint identifiers; names, email addresses, and phone numbers are not canonical identity keys.
- Expose public application ports for in-process composition and versioned API/event contracts for remote composition.
- Do not expose persistence entities, private repositories, shared tables, or implementation classes as cross-capability contracts.
- Every operation must resolve trusted tenant and actor context from the host security boundary where applicable. Never authorize access merely because a caller knows an ID.
- Enforce tenant scope consistently on reads, writes, list/search/count/export, and nested associations.
- Keep domain code independent of HTTP, CDI, database frameworks, messaging brokers, and provider SDKs.
- Use idempotency and correlation context for commands where retries or external side effects can occur; use durable outbox/inbox or an equivalent mechanism when required by delivery guarantees.
- Model lifecycle, deactivation, verification, primary association rules, effective dates, and audit semantics explicitly.
- Do not auto-merge Parties or contact records solely because a name, email, phone, or address matches. Potential duplicates require tenant-safe resolution and auditable decisions.

## 5. Address model and localization

The current `PostalAddress` captures address lines, locality, administrative area, postal code, and ISO alpha-2 country code. Preserve compatibility while assessing whether the public contract needs richer structured components.

For Indonesia, the extension contract should be able to represent province, kabupaten/kota, kecamatan, desa/kelurahan, RT/RW, postal code, street/building/number, and landmarks where appropriate. These should be modeled as structured fields or references when they need validation, reporting, or integration—not buried permanently in one free-text string.

Requirements:

- Keep the canonical postal/physical address separate from geocoding provider details.
- Represent address purpose and validity on the Party-address association, not as universal assumptions on the Address itself.
- Allow an address to be associated with multiple Parties where the business semantics permit it.
- Treat geocoding and administrative-boundary datasets as optional adapters/reference data, not mandatory domain dependencies.
- Preserve transaction-time address snapshots in orders, invoices, bookings, shipments, and other records that require historical evidence. Editing a current Party address must not silently rewrite past transactions.

## 5.1 International address representation

Internationalization is a first-class contract requirement, not a country-specific set of columns.

- Preserve the submitted address text and script. Apply Unicode NFC normalization only where safe; never transliterate or translate canonical address values as a side effect.
- Keep country identity as ISO 3166-1 alpha-2. Country-specific formatting, required components, postal-code rules, and administrative hierarchy belong to versioned country metadata/reference data, not a universal fixed schema.
- Represent components that require validation or integration as typed values or stable references. A country's hierarchy may have different levels and names; do not assume every country has province/state, city, district, and postal code in the same order.
- Indonesia must be representable with province, kabupaten/kota, kecamatan, desa/kelurahan, RT/RW, postal code, street/building/number, building/floor/unit, and landmark/delivery instruction where relevant. These are a country profile over the neutral address contract, not mandatory fields for every country.
- Keep administrative-boundary identifiers and geocoding coordinates/provider responses in Geo/reference-data contracts. An address remains usable when no geocoder or reference-data provider is configured.
- Distinguish a postal address from a geospatial point, service area, or route destination. Do not infer coordinates from free text inside the domain model.
- Country-specific address rendering must be driven by versioned format metadata and locale, and must not rewrite the stored components.

## 5.2 Locale and language ownership

Locale, language, time zone, and currency are related but distinct concepts.

- Use BCP 47 language tags for language/locale identifiers (for example, `id-ID`, `en-ID`, `zh-Hant-TW`). Validate and canonicalize tags at the boundary; reject malformed tags rather than silently converting them to an unrelated default.
- A user's preferred language/locale belongs to the relevant Person/Profile preference contract. A tenant's default locale belongs to tenant configuration. A request-scoped locale override belongs to request context. Do not make a global Party locale authoritative for every tenant or application.
- Support an ordered language preference list where product behavior requires fallback (for example, `id-ID`, then `en`). Keep explicit user preference distinct from the locale selected by fallback.
- Keep time zone as an IANA time-zone identifier and currency as an ISO 4217 code. Neither may be derived implicitly from locale or country.
- Keep locale-aware formatting (dates, numbers, currencies, phone display, address rendering) in presentation/application adapters using maintained locale data such as ICU/CLDR. Domain values remain typed and culture-neutral.
- Localized labels and descriptions for governed attribute definitions may carry BCP 47 language tags. Attribute keys, stored values, identifiers, and business rules must not vary by display language.
- Translation catalogs, message negotiation, plural rules, and UI resource bundles are platform/application concerns; Contact, Profile, and Address must not become a translation service.
- Preserve original user-entered names and address scripts. Optional transliterations are additional derived/display values with provenance, not replacements.

## 5.3 Profile and internationalization boundary

- A generic Person Profile may expose preferred locale/language preferences as profile preferences, but must not own tenant defaults, translation catalogs, authentication locale policy, or legal identity attributes owned elsewhere.
- Organization Profile may expose declared operating languages and a preferred communication language where useful; these do not replace tenant or per-contact preferences.
- ContactPoint values remain endpoint identifiers. Communication language and channel consent are separate association/policy concerns and must be resolved by the owning communication/privacy capability.
- Localized profile display names or descriptions, where required, must preserve the source language and use explicit language tags. Do not silently overwrite a canonical legal name with a translated label.

## 6. Custom and additional attributes

Custom attributes are required, but must be governed metadata—not an untyped map that bypasses domain rules.

### 6.1 Attribute scopes

Support attributes at explicitly declared scopes where there is a real business need:

- Party;
- ContactPoint;
- Address;
- PartyContactPoint association;
- PartyAddress association;
- tenant-defined extension profiles, subject to authorization and schema governance.

Attributes on an association describe the relationship, not the shared record. For example, a delivery instruction may belong to a PartyAddress association, while an accessibility property of a physical location may belong to the Address.

### 6.2 Definition contract

An attribute definition should identify:

- stable namespaced key and owner;
- applicable entity/association scope;
- display label and optional localization;
- data type and schema version;
- required/optional status and default, where applicable;
- validation constraints and allowed values;
- sensitivity classification and read/write policy;
- indexing/search policy;
- lifecycle/deprecation status.

Namespaced keys should distinguish shared definitions from module-owned extensions, for example `contact.preferred_language` and `logistics.delivery_access_notes`. These are illustrative keys, not a commitment to add those exact fields.

### 6.3 Value contract

The type system should support appropriate primitives such as text, integer/decimal, boolean, date/time, single/multiple choice, and stable references. Values must be validated against the active definition version. Invalid or unknown keys must not silently bypass validation.

Do not put security-critical or invariant-bearing state—tenant ownership, canonical IDs, verification state, consent decisions, lifecycle status, or authorization policy—into arbitrary custom attributes.

### 6.4 Reuse and storage decision

Before implementing a generic attribute engine, audit existing attribute-related code and contracts, including product specifications and identity/ABAC attributes. Reuse only if semantics, ownership, tenant isolation, validation, lifecycle, and query requirements genuinely match. Authorization attributes and product-specification attributes are not automatically suitable for general Party metadata.

Choose persistence based on concrete requirements. Do not default to JSON blobs or EAV tables without evaluating constraints, indexing, reporting, migration, and query plans. Sensitive values require explicit access controls and audit treatment.

## 7. Customer and CRM compatibility

- Customer must reference canonical Party identity through a stable explicit mapping.
- CRM's current scalar contact/address fields and nested collections must be inventoried field by field before migration.
- Preserve existing API behavior through compatibility adapters/read models where safe; do not perform a flag-day rewrite.
- Backfills must be restartable, idempotent, observable, and reconcilable.
- Report missing, duplicate, conflicting, and ambiguous mappings. Do not merge records automatically across tenants.
- Do not remove legacy fields/tables until consumers are migrated, data reconciliation is accepted, and rollback/forward-recovery is documented.
- Keep customer-specific classifications, lifecycle, account relationships, and payment/commercial terms in Customer or their actual owning capabilities.

## 8. Security, privacy, and audit

- Enforce tenant scope at application and persistence boundaries.
- Treat contact values and addresses as personal/business data; apply purpose limitation, least privilege, retention, and deletion/anonymization rules appropriate to the record's legal and operational obligations.
- Keep consent and communication preferences under one explicitly designated owner with purpose, channel, provenance, effective time, and revocation semantics.
- Audit sensitive changes, verification transitions, merge decisions, and changes to primary/effective associations with actor, time, reason, and correlation context as applicable.
- Prevent cross-tenant existence leaks in lookup and duplicate-resolution paths.
- Avoid logging full contact values, delivery instructions, or sensitive custom attributes by default.

## 9. Milestones and acceptance criteria

### M0 — Ownership and implementation audit

- Read root and applicable nested `AGENTS.md` instructions and relevant ADRs.
- Inventory Party, ContactPoint, Address, association, persistence, migrations, API/event contracts, attribute systems, and all consumers.
- Produce a field-by-field ownership and migration map for CRM Customer and any other legacy contact/address representations.
- Identify actual Maven reactor roots and reproducible narrow test commands.
- Record known gaps without claiming unverified runtime behavior.

### M1 — Canonical contract

- Confirm public Party/ContactPoint/Address and association semantics.
- Decide which address fields are core versus localization/reference-data extensions.
- Specify custom attribute definitions, scopes, type validation, namespace, sensitivity, and lifecycle.
- Add or update architecture tests for dependency direction and tenant-safe access boundaries where supported by the existing test architecture.

### M2 — Persistence and compatibility

- Verify existing persistence and migrations before adding tables or columns.
- Add additive, forward-recoverable schema changes only where justified.
- Implement compatibility mapping and idempotent backfill with reconciliation evidence.
- Preserve transaction snapshots and audit history.

### M3 — Consumer integration

- Migrate Customer and CRM through public contracts.
- Inventory and integrate other consumers only where their current ownership and dependency evidence supports it.
- Test standalone and modular-monolith composition; define remote contracts only where a distributed boundary is required.

### M4 — Production hardening

- Verify tenant isolation, authorization, privacy/retention, audit, duplicate resolution, schema evolution, indexing, observability, and API compatibility.
- Run the narrowest affected reactor tests, then relevant dependent reactors and CI where practical.
- Report exact commands, outcomes, skips, and environmental limitations.

## 10. Pull request policy

Deliver coherent milestone-sized PRs, not a PR for each small change. Do not merge PRs without explicit authorization. A PR must describe the observed problem, owning capability, contracts/data/events changed, migration and compatibility impact, tests actually run, and remaining risks.

No destructive migration or automatic cross-tenant merge is authorized by this contract.
