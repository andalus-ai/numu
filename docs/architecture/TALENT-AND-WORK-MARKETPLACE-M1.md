# Talent & Work Marketplace — M1 Talent Profile Foundation

- **Status:** Initial domain implementation on a feature branch; persistence and application APIs are not yet implemented.
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

## 5. Security and privacy constraints

- Public visibility does not bypass tenant, consent, moderation, block-list or privacy checks.
- Profile lookup must apply authorization before returning profile data.
- Resume files and portfolio binaries must be stored through Document and referenced by stable document IDs; never store untrusted binary content in the profile aggregate.
- Search indexing must include only fields approved by profile visibility and consent.
- Sensitive data must not be placed in the headline, summary or location label by application convention; validation and moderation policy must be added before public launch.
- Profile creation must resolve tenant and actor from trusted authenticated context at the application boundary, never from a caller-controlled tenant header/body field.

## 6. Follow-up implementation sequence

1. M1b: profile skills, experience, education, credential and portfolio references with explicit verification provenance and privacy.
2. M1c: application ports, tenant-scoped repository contract, command/query handlers, authorization policy and deterministic in-memory adapter for domain/application tests.
3. M1d: PostgreSQL persistence, migrations, tenant-scoped constraints, optimistic concurrency and integration tests.
4. M2: job vacancy aggregate, publication lifecycle, search criteria and employer authorization.
5. M3: recruitment application aggregate and ATS pipeline.
6. M4–M5: freelance and crowdsourcing engagement lifecycles.
7. M6: matching, reputation and verified contribution history.

## 7. Verification status

Domain unit tests are included in this increment. Build execution, persistence, REST/API exposure, search integration and production authorization are not claimed by this document. The domain module must be built with the framework reactor and the test result recorded before merge.
