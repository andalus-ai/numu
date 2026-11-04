# Talent & Work Marketplace — M1 Talent Profile Foundation

- **Status:** M1a–M1b domain and M1c application increments committed on a feature branch; PostgreSQL persistence and transport adapters remain pending.
- **Owner:** Talent capability.
- **Repository:** `andalus-ai/numu`.
- **Scope:** establish the first production-oriented domain boundary for professional profiles.

## 1. Product boundary

Talent & Work Marketplace is a reusable capability family for job vacancies, recruitment, freelance engagements and crowdsourcing. It is not an extension of HRIS and does not own canonical identity, authentication, contact points, workforce employment, contracts, payment execution or accounting.

The first increment creates the tenant-scoped professional profile aggregate. Job marketplace, recruitment/ATS, freelance and crowdsourcing remain separate bounded contexts to be added in later milestones.

## 2. Ownership and reuse

| Concern | Owner |
|---|---|
| Canonical person/organization identity | Ecosystem Party |
| Login and authentication | Identity |
| Professional presentation | Talent Profile |
| Internal worker/employment lifecycle | Workforce |
| Skill and qualification catalogue | Existing Workforce Skill/Qualification for internal workforce use; shared talent taxonomy extraction requires a separate compatibility milestone |
| Employer identity | Organization / Party |
| Candidate applications and hiring pipeline | Recruitment (future) |
| Vacancies and publication lifecycle | Job Marketplace (future) |
| Freelance proposals and engagements | Freelance (future) |
| Open tasks, challenges and bounty submissions | Crowdsourcing (future) |
| Documents and binary content | Document capability |
| Contracts, payment, wallet and accounting | Existing respective capabilities |

## 3. Identity and tenant contract

- A profile references an existing canonical `PartyRef` whose kind must be `PERSON`.
- No implicit `PersonRef` to `PartyId` conversion is introduced.
- A profile is tenant-scoped. A Party identifier is not authorization to read or mutate the profile.
- Profile visibility is a domain preference, not an authorization grant. Application/query boundaries must enforce tenant sharing, consent, actor permissions and privacy policy.
- No login account is required to exist merely because a Party profile exists.
- Contact information, government identifiers, health information, compensation and private employment details are explicitly outside this aggregate.

## 4. M1 domain behavior

The initial aggregate provides:
- tenant-scoped identity reference to a canonical person Party;
- bounded headline, summary and location label;
- private-by-default visibility (`PRIVATE`, `NETWORK`, `PUBLIC`);
- publication lifecycle (`DRAFT`, `PUBLISHED`, `SUSPENDED`, `ARCHIVED`);
- explicit transitions and domain events for creation, publication and visibility changes;
- immutable subject and tenant identity after creation.

The domain has no framework, transport, persistence or broker dependency. The only cross-capability domain dependency is the public Ecosystem Party reference contract.

### M1b — professional profile sections

The profile now supports immutable, bounded collections for:
- skills with proficiency and self-reported / issuer-verified / platform-verified provenance;
- work-experience presentation with validated date ranges and an explicit current-role flag;
- education history;
- credentials with issuer, issue/expiry dates, optional Document reference and verification provenance;
- portfolio items referencing either an HTTPS URL or a stable Document identifier.

Each section is replaced atomically, is capped at 100 entries, rejects duplicate entry IDs, and is immutable to callers. Each successful replacement increments aggregate version and emits a tenant-scoped `talent.profile.content-changed` event naming the changed section. Career history is presentation data only: it does not create or mutate Workforce employment records. Skills remain profile claims; this increment deliberately does not invent a shared skill taxonomy or depend on Workforce's internal catalogue. Credential verification values are provenance labels, not proof that verification has occurred. A trusted application service must validate issuer evidence before recording a verified status.

Portfolio and credential files are referenced by Document IDs; binary content is never stored in the aggregate. HTTPS portfolio links reject embedded credentials. URL allow-listing, malware scanning, document access checks and remote-link safety belong in application/adapters before publication.

### M1c — application boundary and authorization

The `talent-application` module now exposes a framework-neutral Mutiny application service and outbound ports for:
- tenant-explicit profile lookup/save;
- canonical PERSON Party existence validation in the active tenant;
- authorization decisions for create, update, visibility changes, publish and visibility-tier reads;
- evidence verification before skill or credential claims can be marked issuer/platform verified;
- transaction-scoped profile persistence and event/outbox publication.

All commands and reads require a trusted `ExecutionContext` with a non-anonymous actor and a tenant. The tenant is never accepted from a command payload. Repository lookup includes the tenant ID, and a cross-tenant lookup is indistinguishable from not-found. The application service returns immutable `TalentProfileView` projections, not mutable aggregate instances.

Authorization is delegated to a mandatory policy port so host applications can enforce owner/member roles, tenant sharing, consent, blocks, moderation and employer/candidate permissions without embedding a second identity or ACL system in Talent. The policy is evaluated before a profile view is returned and before aggregate mutation. A transaction runner receives the full execution context; the transaction contract requires state persistence and event/outbox append to commit or roll back together. Event publication receives the same actor, tenant and correlation metadata. Presentation changes emit a privacy-safe event containing identifiers and event metadata only, never the headline or summary text.

A deterministic in-memory repository/unit-of-work test adapter exercises tenant isolation, party existence, anonymous-context rejection, denied reads, denied writes and rejected unverified skill claims. It is test-only and is not represented as a production persistence adapter.

### M1d-a — persistence-safe aggregate rehydration

The domain aggregate now exposes an explicit `rehydrate(...)` factory for persistence adapters. It restores the full profile snapshot, including all five profile sections, lifecycle state, creation/update timestamps and the exact optimistic-lock version. Rehydration does not emit a creation event or replay lifecycle transitions. It reuses the aggregate's bounded collection and identity validation, rejects negative versions and invalid timestamp ordering, and enforces the archived/private visibility invariant.

The application persistence port now separates `insert(...)` from `update(..., expectedVersion)`, preventing accidental update-as-upsert behavior. The application service captures the loaded version before mutation and passes it explicitly to the update port; a concrete adapter must include tenant, profile ID and expected version in its conditional update predicate and reject a zero-row update as an optimistic conflict. The in-memory application test double exercises the stale-version rejection contract.

This is still a domain/application contract only: no PostgreSQL schema, JDBC adapter or production transaction implementation is implied yet. A production adapter must append outbox events in the same database transaction as the aggregate write.

## 5. Security and privacy constraints

- Public visibility does not bypass tenant, consent, moderation, block-list or privacy checks.
- Profile lookup must apply authorization before returning profile data.
- Resume files and portfolio binaries must be stored through Document and referenced by stable document IDs; never store untrusted binary content in the profile aggregate.
- Search indexing must include only fields approved by profile visibility and consent.
- Sensitive data must not be placed in the headline, summary or location label by application convention; validation and moderation policy must be added before public launch.
- Profile creation must resolve tenant and actor from trusted authenticated context at the application boundary, never from a caller-controlled tenant header/body field.

## 6. Follow-up implementation sequence

1. M1a: profile identity, presentation and lifecycle domain.
2. M1b: skills, experience, education, credential and portfolio references with explicit provenance and bounded immutable sections.
3. M1c: application ports, tenant-scoped repository contract, command/query handlers, authorization policy and deterministic in-memory adapter for domain/application tests.
4. M1d: PostgreSQL persistence, migrations, tenant-scoped constraints, optimistic concurrency and integration tests.
5. M2: job vacancy aggregate, publication lifecycle, search criteria and employer authorization.
6. M3: recruitment application aggregate and ATS pipeline.
7. M4–M5: freelance and crowdsourcing engagement lifecycles.
8. M6: matching, reputation and verified contribution history.

## 7. Verification status

Domain and application unit tests are included in this increment. Build execution, PostgreSQL persistence, REST/API exposure, search integration and a concrete production authorization policy are not claimed by this document. The domain module must be built with the framework reactor and the test result recorded before merge.
