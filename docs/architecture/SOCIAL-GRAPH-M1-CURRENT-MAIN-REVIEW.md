# Social Graph M1 — Current-main implementation review

- **Review date:** 2026-11-04
- **Reviewed base:** `main` at `d51bba2cb192c84fe092b631fa98c6c5ea808770`
- **Review branch:** `feat/social-graph-m1-ecosystem-identity-adapters`
- **Status:** Trusted command facade and Ecosystem identity adapters added on feature branch; build, PostgreSQL and host-runtime verification pending
- **Related:** [Foundation architecture](SOCIAL-GRAPH-FOUNDATION-ARCHITECTURE.md), [M1 implementation plan](SOCIAL-GRAPH-M1-IMPLEMENTATION.md), [M0 repository audit](SOCIAL-GRAPH-M0-REPOSITORY-AUDIT.md)

## 1. Current implementation found on main

The earlier M0-only assumption is superseded. Current `main` contains:

- `core/framework/social-graph/social-graph-domain`
- `core/framework/social-graph/social-graph-spi`
- `core/framework/social-graph/social-graph-application`
- `core/framework/social-graph/social-graph-persistence-jdbc`
- `.github/workflows/social-graph.yml`
- `docs/architecture/SOCIAL-GRAPH-M1-IMPLEMENTATION.md`

The Social Graph reactor is included by `core/framework/pom.xml`. The dedicated workflow configures Java 21 and runs:

```bash
mvn -B -ntp -f core/framework/pom.xml -pl social-graph -am verify
```

This is configured CI behavior, not evidence that the workflow has passed for the reviewed revision. No Maven build or Testcontainers test was executed in this review session.

## 2. M1 behavior confirmed by source

- `SocialConnection` models a tenant-scoped bilateral connection with request, accept, reject, cancel and remove transitions.
- The domain enforces participant-specific transitions: recipient accepts/rejects, requester cancels, either participant removes an active connection.
- The aggregate references canonical Ecosystem `PartyId`; it does not create a second person or organization identity.
- The current `main` has the Ecosystem public ports (`PartyIdentityAssociationRepository` and `PartyLookupPort`) but no concrete Social Graph adapter in the application package. The feature branch adds `EcosystemSocialGraphActorResolver` and `EcosystemSocialGraphPartyDirectoryAdapter` against those public contracts.
- `SocialGraphService` validates active Parties, tenant-scoped idempotency keys and command fingerprints, and uses optimistic version checks.
- PostgreSQL enforces one pending/active connection per unordered Party pair per tenant with a partial unique index.
- The JDBC unit of work executes connection, idempotency, audit and outbox writes through one physical connection and one transaction.
- Audit rows are intended to be append-only through a PostgreSQL trigger.
- PostgreSQL is authoritative; no Neo4j runtime dependency is present. The outbox is an event-intent store only; relay/CDC and DLQ operations are excluded from M1.

## 3. Targeted defects confirmed present on reviewed main

### 3.1 PostgreSQL trigger function delimiter

The previously reviewed migration used a lone `# Social Graph M1 — Current-main implementation review

- **Review date:** 2026-11-04
- **Reviewed base:** `main` at `d51bba2cb192c84fe092b631fa98c6c5ea808770`
- **Review branch:** `feat/social-graph-m1-ecosystem-identity-adapters`
- **Status:** Trusted command facade and Ecosystem identity adapters added on feature branch; build, PostgreSQL and host-runtime verification pending
- **Related:** [Foundation architecture](SOCIAL-GRAPH-FOUNDATION-ARCHITECTURE.md), [M1 implementation plan](SOCIAL-GRAPH-M1-IMPLEMENTATION.md), [M0 repository audit](SOCIAL-GRAPH-M0-REPOSITORY-AUDIT.md)

## 1. Current implementation found on main

The earlier M0-only assumption is superseded. Current `main` contains:

- `core/framework/social-graph/social-graph-domain`
- `core/framework/social-graph/social-graph-spi`
- `core/framework/social-graph/social-graph-application`
- `core/framework/social-graph/social-graph-persistence-jdbc`
- `.github/workflows/social-graph.yml`
- `docs/architecture/SOCIAL-GRAPH-M1-IMPLEMENTATION.md`

The Social Graph reactor is included by `core/framework/pom.xml`. The dedicated workflow configures Java 21 and runs:

```bash
mvn -B -ntp -f core/framework/pom.xml -pl social-graph -am verify
```

This is configured CI behavior, not evidence that the workflow has passed for the reviewed revision. No Maven build or Testcontainers test was executed in this review session.

## 2. M1 behavior confirmed by source

- `SocialConnection` models a tenant-scoped bilateral connection with request, accept, reject, cancel and remove transitions.
- The domain enforces participant-specific transitions: recipient accepts/rejects, requester cancels, either participant removes an active connection.
- The aggregate references canonical Ecosystem `PartyId`; it does not create a second person or organization identity.
- The current `main` has the Ecosystem public ports (`PartyIdentityAssociationRepository` and `PartyLookupPort`) but no concrete Social Graph adapter in the application package. The feature branch adds `EcosystemSocialGraphActorResolver` and `EcosystemSocialGraphPartyDirectoryAdapter` against those public contracts.
- `SocialGraphService` validates active Parties, tenant-scoped idempotency keys and command fingerprints, and uses optimistic version checks.
- PostgreSQL enforces one pending/active connection per unordered Party pair per tenant with a partial unique index.
- The JDBC unit of work executes connection, idempotency, audit and outbox writes through one physical connection and one transaction.
- Audit rows are intended to be append-only through a PostgreSQL trigger.
- PostgreSQL is authoritative; no Neo4j runtime dependency is present. The outbox is an event-intent store only; relay/CDC and DLQ operations are excluded from M1.

## 3. Targeted defects confirmed present on reviewed main

### 3.1 PostgreSQL trigger function delimiter

 as a PostgreSQL dollar-quote delimiter around the audit trigger function. PostgreSQL dollar-quoted function bodies require a matching valid delimiter such as `$`. Current `main` now contains the corrected `AS $ ... $;` function body.

File: `core/framework/social-graph/social-graph-persistence-jdbc/src/main/resources/db/migration/V1__social_graph_connections.sql`

Commit: `9106e610e2903c4eec8654c5ce1b4581ba26d265`

### 3.2 Mutiny completion in UUID-ordering integration test

The previously reviewed PostgreSQL UUID-ordering test did not await the insert operation or the `Uni<Optional<SocialConnection>>` before asserting `isPresent()`. Current `main` now awaits both operations.

File: `core/framework/social-graph/social-graph-persistence-jdbc/src/test/java/tech/kayys/numou/socialgraph/persistence/jdbc/JdbcSocialGraphUnitOfWorkPostgresTest.java`

Commit: `7a9375a4e8520f79cdebd702ac495b35b38e8d77`

Both repairs are present in current `main`; they were source-reviewed but have not yet been compiled or run in this work session.

## 4. Security and tenant-boundary findings

### Confirmed strengths

- Actor resolution is explicitly separate from authorization.
- Identity mapping uses tenant + issuer/subject association and canonical Party lookup; no UUID casting between UserId, PersonRef, OrganizationId and PartyId is required.
- SQL reads and updates inspected include tenant predicates. Composite primary/foreign keys keep connection, audit and idempotency records tenant-associated.
- The integration test source includes a tenant-scoped lookup test.

### Remaining M1 integration gate

- `SocialGraphService` invokes `SocialGraphAuthorizationPort` before Party checks or persistence for connection requests and lifecycle commands.
- `SocialGraphActor` remains publicly constructible, so direct service exposure would allow callers to supply an arbitrary actor context. The feature branch makes `SocialGraphService` package-private and exposes `SocialGraphCommandFacade` as the public command entry point.
- The facade resolves a host-verified issuer/subject against the trusted tenant through `EcosystemSocialGraphActorResolver`, rejects missing/cross-tenant/inactive associations, and delegates to the service. The service still requires host-provided tenant membership and capability authorization.
- Facade tests cover all five commands, missing identity association, cross-tenant actor rejection, and authorization denial before persistence. Adapter tests cover tenant/subject matching and Party lifecycle.
- No HTTP/messaging host composition was found in this Social Graph slice. The host must still populate trusted tenant/issuer/subject from verified authentication context and provide a fail-closed `SocialGraphAuthorizationPort` implementation.
- The Social Graph tables do not currently declare PostgreSQL row-level security policies. The persistence adapter applies tenant predicates. Decide whether database RLS is required as defense-in-depth for the intended deployment roles before exposing broader read/query APIs.

## 5. Adjacent capability ownership revalidated

### Talent and recruitment

- `docs/architecture/TALENT-AND-WORK-MARKETPLACE-M1.md` identifies a separate Talent capability family.
- Current source contains `talent/talent-domain` only, with a tenant-scoped `TalentProfile` referencing a canonical PERSON `PartyRef`, private-by-default visibility, bounded profile sections and publication lifecycle.
- The profile explicitly excludes authentication, contact points, identity documents, employment records and candidate applications.
- The architecture document says application APIs and persistence are not yet implemented. Job Marketplace and Recruitment/ATS are future bounded contexts (vacancies planned for M2, recruitment applications for M3).
- Workforce's existing `domain/talent` package is succession planning/review, not the public job marketplace. Do not move public candidate profiles or applications into HRIS.

### Fleet, Mobility and Courier

- Fleet uses its own `fms.domain.identifier.DriverId`; Mobility has a separate `mobility.kernel.domain.DriverId`. Neither is a PartyId.
- Fleet `Driver` owns driver personal, employment, licence, safety, training, performance and availability data. This is operational/HR data, not Social Graph profile state.
- Fleet `Vehicle` uses string owner/current-driver references and owns vehicle asset, registration, maintenance and telematics details. No typed Party mapping was established in this inspection.
- Courier `NetworkPartner` and `CourierNetworkPartner` are tenant-owned carrier/agent directory records with their own IDs and status. Neither inspected record establishes a Party association.
- Social Graph may store relationship references only after an explicit, validated mapping contract exists. It must not infer DriverId, VehicleId, Carrier/Partner ID or PartyId equivalence from UUID/string shape.

### Marketplace

- `MarketplaceOrder` owns commercial order lifecycle and line snapshots, not buyer/seller identity, inventory, payment, fulfillment or authorization.
- Buyer and seller identifiers in the inspected aggregate are strings; `MarketplaceOrderAuthorization` explicitly requires trusted identity/membership in its implementation contract.
- Social Graph should integrate through explicit Party mapping and discovery policies, not copy marketplace commercial order or seller qualification state.

## 6. Privacy and consent ownership

The current repository tree contains capability-specific privacy/retention behavior, but no generalized platform-wide data-subject consent/erasure capability was identified in this scan.

- Mobility migration `V301__mobility_traces_privacy.sql` defines tenant RLS for trip traces, retention purge defaults, and a rider-erasure function that pseudonymizes finished ride locations, deletes traces and redacts ride chat. This is Mobility-specific behavior and requires its dedicated maintenance role.
- Syirkah `LifecycleConsent` is contract/lifecycle consent, not a universal social-network privacy preference.
- Project `ProjectRetention` and restaurant guest-retention migrations concern their respective commercial/operational lifecycles, not general data-subject erasure.
- Talent profile visibility is a presentation preference, not authorization or consent.
- Social Graph M1 currently has no consent, block, profile-visibility or privacy-policy enforcement. These must be established before public discovery, multi-hop traversal, search indexing or cross-tenant federation.

## 7. M1 acceptance gates and next work

1. Run the dedicated Social Graph workflow / Maven command and record exact results; verify the Flyway migration and Testcontainers PostgreSQL tests execute (not silently skipped because Docker is unavailable).
2. Wire the facade and Ecosystem adapters into an actual host using verified authentication context; provide and test a fail-closed tenant membership/capability authorization implementation.
3. Decide and document PostgreSQL RLS requirements for graph, audit, idempotency and outbox tables under actual application/worker roles.
4. Add concurrency coverage for duplicate idempotency keys and active-pair races, and confirm retry/replay behavior.
5. Define consent, visibility, block and deletion semantics before adding read/list/search or cross-tenant discovery.
6. Add bounded read/list APIs only after authorization and privacy policy are enforceable.
7. Keep Neo4j optional. Add a projection adapter only after measured query workloads justify it and the outbox relay/reconciliation contract is ready.

No PR is opened by this review. A review-ready PR still requires explicit approval and CI evidence.
