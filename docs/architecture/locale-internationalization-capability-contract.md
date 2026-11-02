# Locale and Internationalization Capability Contract

- **Status:** Proposed platform contract; implementation is incremental
- **Scope:** Locale/language preferences and localization behavior consumed by Contact, Profile, Address, and other Numou capabilities
- **Related:** [ADR-003 — Reusable capability modules](ADR-003-reusable-capability-modules.md), [Party, Contact, Address, and Custom Attribute Capability Contract](contact-address-capability-contract.md), [Domain Capability Matrix](DOMAIN-CAPABILITY-MATRIX.md)

## 1. Purpose and decision

Numou must support Indonesian-first products without becoming Indonesia-only. Internationalization is a platform concern with explicit contracts; it must not be implemented as scattered country conditionals or by making every domain aggregate depend on a translation framework.

Locale-sensitive presentation and language negotiation are distinct from canonical business data. Domain models store stable identifiers and culture-neutral values. Application/presentation adapters select localized representations using explicit context and maintained locale data.

This contract defines ownership and behavior. It does not claim that a shared locale module, locale persistence, translation runtime, or address reference-data service already exists.

## 2. Vocabulary

| Concept | Meaning | Canonical representation |
|---|---|---|
| Language | Human language preference | BCP 47 language tag |
| Locale | Language plus optional script, region, and extensions used for conventions | Canonical BCP 47 tag |
| Time zone | Rules for local civil time | IANA TZDB identifier |
| Currency | Monetary unit | ISO 4217 code |
| Country | Country/territory identity | ISO 3166-1 alpha-2 code, subject to domain policy |
| Script | Writing system | BCP 47/ISO 15924 script subtag where applicable |
| Translation | Localized message/resource content | Resource key plus language-tagged catalog entry |
| Transliteration | Derived representation in another script | Separate value with method/provenance |

Do not infer a time zone, currency, or language solely from a locale or country. For example, a user may use `en-ID` while preferring a different time zone or operating with multiple currencies.

## 3. Locale identifier contract

- Use BCP 47 language tags; examples include `id`, `id-ID`, `en-ID`, `en-US`, and `zh-Hant-TW`.
- Parse and canonicalize at trusted boundaries using a standards-compliant implementation. Reject malformed/empty tags and the undetermined `und` value for explicit user preferences.
- Preserve the distinction between a language-only preference (such as `id`) and a region-specific locale (such as `id-ID`). Do not invent a region when none was supplied.
- Do not create a home-grown parser or a fixed enum of supported languages.
- Keep a stable fallback policy separate from the requested preference. A fallback must be observable and must not overwrite the user's saved preference.
- Support language negotiation against an application's supported locales. The selected locale is request/application context, not a mutation of Party/Profile.
- Store the original requested tag only when audit or user-experience requirements need it; otherwise store the canonical form.

## 4. Ownership and resolution precedence

| Scope | Owner | Responsibility |
|---|---|---|
| User preferred language/locale | Person/Profile preference contract | Explicit user choice and ordered preferences |
| Tenant default locale | Tenant configuration capability | Default for tenant-managed experiences |
| Application supported locales | Application/platform configuration | Available translations and negotiation policy |
| Request override | Authenticated request context | Temporary explicit override, validated against policy |
| Contact language preference | Communication/consent owner or contact relationship policy | Language for a specific communication purpose/recipient |
| Address rendering locale | Address presentation adapter | Locale-aware ordering, labels, and postal rendering |
| Translation catalogs | Application/platform localization infrastructure | Message keys, pluralization, interpolation, versioning |

Suggested resolution order (subject to application policy): valid explicit request override, user preference, tenant default, application default. Security, legal, and communication workflows may define narrower policies. Never use this UI locale resolution to change legal names, canonical address values, consent, or transaction data.

## 5. Locale-aware formatting

- Use maintained CLDR/ICU-compatible libraries in adapters for date, number, currency, list, plural, and locale display formatting.
- Store instants as instants and local schedules with an explicit time-zone/zone-rule policy. Do not store formatted date strings as canonical time.
- Store money as amount plus ISO currency under the Money contract. Formatting must not change amount or currency.
- Store phone endpoints in a normalized international representation where valid (E.164 for supported telephone numbers); keep display formatting locale-aware and do not treat display strings as identifiers.
- Render addresses using country-specific format metadata plus an explicit display locale. Address format rules must be versioned and testable.
- Keep domain validation independent of the host's default JVM locale/time zone. Tests must set inputs explicitly and must not rely on machine defaults.

## 6. Localized content and attributes

- Translation resources use stable message keys, not translated text as identifiers.
- Localized values carry an explicit BCP 47 language tag and a defined fallback policy.
- Missing translations must follow deterministic fallback behavior and must not silently substitute an unrelated language.
- Attribute definitions may provide localized display labels/descriptions. Attribute keys, types, validation, sensitivity, authorization, and stored values remain language-independent.
- Do not put translated copies of canonical identifiers, legal names, contact values, or address components into untyped custom-attribute maps.
- User-entered multilingual text must preserve Unicode content. Apply Unicode normalization only where the field contract permits it; do not strip combining marks, change scripts, or transliterate automatically.

## 7. International address and administrative metadata

- The neutral address contract stores country and address components without assuming a universal hierarchy or component order.
- Country-specific rules describe required/optional components, labels, postal-code validation, component ordering, and script/display behavior. Rules and reference datasets must be versioned with source/provenance.
- Indonesia's province, kabupaten/kota, kecamatan, desa/kelurahan, RT/RW, postal code, street/building/number, unit, and landmark needs must be representable through the address extension/reference-data contract.
- Other countries may have different levels or no postal code. Do not require Indonesian administrative fields globally.
- Administrative codes and geographic identifiers should reference the Geo/reference-data capability where available. Address creation must remain possible when no external geocoder is configured.
- Do not use geocoding results as proof of address ownership, identity, or deliverability.
- Retain user-entered address text and script alongside any normalized/reference-linked representation where reconciliation requires it.

## 8. Compatibility, security, and privacy

- Locale and language preferences are personal preference data and must follow tenant authorization, privacy, export, correction, and deletion policies.
- Do not expose another tenant's profile preference through Party lookup.
- Avoid logging full preference profiles or user-entered localized content unnecessarily.
- Changes to supported locale sets and fallback rules must be backward-compatible or versioned where they affect API behavior.
- Persist canonical tags, not JVM Locale serialization or machine-default strings.
- API contracts should declare whether a locale is input, selected context, or response metadata.
- Events should carry the selected locale only when downstream processing genuinely needs it; otherwise carry a stable message key and resolve localization at delivery time.

## 9. Acceptance criteria

1. BCP 47 tags are parsed, canonicalized, and rejected deterministically when invalid.
2. Language-only and region-specific preferences remain distinct.
3. Resolution/fallback is deterministic and does not overwrite saved preferences.
4. Time zone and currency are never inferred implicitly from locale.
5. Formatting tests are independent of host JVM defaults.
6. Multilingual text and scripts are preserved without implicit transliteration.
7. Address rendering is country-aware and locale-aware, with Indonesia and at least one materially different international format represented in tests.
8. Translation catalogs and formatting adapters are not dependencies of pure domain models.
9. Tenant-scoped profile preference access is tested.
10. API/event contracts distinguish requested locale, selected locale, and canonical stored data.

## 10. Implementation sequence

- **M1 — Contract:** agree vocabulary, ownership, BCP 47 behavior, locale resolution, and international address requirements.
- **M2 — Domain primitives and profile preferences:** implement only after auditing existing shared locale/language/time-zone types; avoid duplicate primitives.
- **M3 — Address format/reference-data adapters:** introduce versioned country metadata and optional Geo integration.
- **M4 — Application localization integration:** message catalogs, negotiation, and locale-aware formatting adapters.
- **M5 — Consumer migration:** progressively adopt in Workspace, CRM, Customer, Commerce, Courier, Mobility, and other consumers through public contracts.

## 11. Non-goals

This contract does not make Contact/Profile/Address the owner of translation catalogs, tenant configuration, authentication policy, time-zone databases, currency conversion, geocoding, legal identity, or consent. Those remain with their respective platform or business capabilities.
