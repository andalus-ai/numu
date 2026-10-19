# Kayys ERP Platform

A modular ERP microservice platform: SOLID, DDD, CQRS. This repo now
contains the **foundation** (framework-free building blocks every
bounded context sits on) plus reusable bounded contexts under
`Domain/`. Identity remains the reference vertical slice, while the
other extracted capabilities can be composed by products without
coupling them to the draft monolith.

## Modules

```text
numou-platform/
├── foundation/
│   ├── domain/         numou-foundation        pure Java DDD kit
│   ├── application/     numou-foundation-application    CQRS kit (Mutiny)
│   └── testing/         numou-foundation-testing        shared test doubles + ArchUnit rules
├── Domain/
│   ├── identity/       identity bounded context
│   ├── catalog/        product/catalog capability
│   ├── asset/          fixed-asset capability
│   └── ...             other extracted bounded contexts
```

## BOM

Products should import `tech.kayys.numou:numou-bom` with
`<type>pom</type>` and `<scope>import</scope>` in their
`dependencyManagement`. The BOM aligns the Framework version, Quarkus/JUnit
versions inherited from the parent, Foundation artifacts, and extracted
bounded-context artifacts. Product POMs then declare module dependencies
without repeating versions.

```xml
<dependencyManagement>
  <dependencies>
    <dependency>
      <groupId>tech.kayys.numou</groupId>
      <artifactId>numou-bom</artifactId>
      <version>0.1.0-SNAPSHOT</version>
      <type>pom</type>
      <scope>import</scope>
    </dependency>
  </dependencies>
</dependencyManagement>
```

Dependency direction is one-way, enforced by ArchUnit tests in every
module, not just by convention:

```text
Infrastructure (adapter)  →  Application  →  Domain
```

## Foundation

### `numou-foundation` — pure Java, zero runtime dependencies

```text
tech.kayys.numou.foundation.domain
├── identifier    DomainId<T>
├── entity        Entity<ID>, AggregateRoot<ID>, AbstractAggregateRoot<ID>
├── valueobject   ValueObject, Money, Currency, Quantity, Unit, Percentage, DateRange
├── event         DomainEvent
├── time          DomainClock
└── exception     DomainException, BusinessRuleViolation, InvalidStateException
```

No `@Entity`, `@Path`, `@Inject`, no Mutiny `Uni`, no JSON annotations.
An aggregate exposes plain Java methods (`user.activate()`), not
`Uni<User> activate()` — reactive orchestration is an *application*
concern, not a domain one.

### `numou-foundation-application` — CQRS building blocks, reactive orchestration

```text
tech.kayys.numou.foundation.application
├── command       Command, CommandHandler<C, R>
├── query         Query, QueryHandler<Q, R>
├── result        Result<T>, ApplicationError, ApplicationErrorException
├── page          Page<T>, PageRequest
├── event         EventPublisher            (outbound port)
└── transaction   UnitOfWork                (transaction-boundary port)
```

Allowed to depend on Mutiny and `numou-foundation` — nothing
else. `CommandHandler`/`QueryHandler` are where `Uni` orchestration
lives; the aggregates they call stay synchronous.

### `numou-foundation-testing` — shared test doubles + ArchUnit rules *(new)*

```text
tech.kayys.numou.foundation.testing
├── time            FixedDomainClock             deterministic DomainClock test double
└── architecture    ForbiddenDependencyRules     shared "no framework leakage" ArchUnit rules
```

Added so every downstream module (`identity`, and whatever comes
next) writes its architecture test as one line —
`ForbiddenDependencyRules.checkNoDependencyOn(...)` — instead of
re-declaring and slowly drifting out of sync with its own
forbidden-package list. `numou-foundation-application`'s own
`ApplicationArchitectureTest` has been refactored to use it too.

**Not** used by `numou-foundation` itself: that module is a
compile dependency of `numou-foundation-testing`, so the reverse
dependency would create a cycle in the Maven reactor.
`DomainArchitectureTest` stays self-contained for that reason — noted
directly in its Javadoc so it doesn't look like an oversight later.

## Identity bounded context *(new)*

The first bounded context, built to prove the foundation's pattern
holds up in practice — not a toy, a complete vertical slice.

### `numou-identity-domain`

```text
tech.kayys.numou.identity.domain
├── user          UserId, User (aggregate root), UserStatus
├── valueobject   EmailAddress, PasswordHash, DisplayName
├── event         UserRegistered, UserActivated, UserEmailChanged, UserDeactivated
└── exception     UserAlreadyActiveException, UserAlreadyDeactivatedException
```

`User` is deliberately narrow: authentication identity only. It does
**not** know about tenants, organizations, roles, or permissions —
those are Organization/Authorization concerns that compose with
Identity from the outside (e.g. a separate `Membership` aggregate
linking a `UserId` to a `TenantId` with a `Role`), which is what lets
`User` be reused unmodified across POS, e-commerce, and marketplace
products with very different tenancy models. Uniqueness of email is
*not* enforced inside the aggregate either — that requires querying
other users, which only the application layer (via the repository
port) can do.

### `numou-identity-application`

```text
tech.kayys.numou.identity.application
├── command   RegisterUserCommand, ChangeUserEmailCommand, DeactivateUserCommand (+ handlers)
├── query     GetUserByIdQuery, ListUsersQuery (+ handlers), UserView (read projection)
└── port      UserRepository, PasswordHasher
```

`UserRepository` is aggregate-specific — `findById`, `findByEmail`,
`findAll(PageRequest)`, `save` — not a generic `Repository<T, ID>`.
`PasswordHasher` is deliberately synchronous: hashing is CPU-bound,
not I/O-bound, so wrapping it in a `Uni` would add nothing.

### `numou-identity-adapter`

The only module allowed to depend on Quarkus/Hibernate/Kafka directly:

```text
tech.kayys.numou.identity.adapter
├── inbound/rest        UserResource (JAX-RS), ApplicationErrorExceptionMapper, DTOs
├── outbound/postgres    UserEntity (Panache), PostgresUserRepository
├── outbound/security     JBcryptPasswordHasher
├── outbound/messaging    KafkaEventPublisher (SmallRye Reactive Messaging)
├── outbound/transaction  HibernateReactiveUnitOfWork
└── bootstrap             SystemDomainClock, IdentityHandlerProducers (CDI wiring)
```

`ApplicationErrorExceptionMapper` is the *only* place an
`ApplicationError.code()` gets mapped to an HTTP status — handlers
never know about HTTP, the mapper never knows about business rules.

`KafkaEventPublisher` publishes directly, not yet through a
transactional outbox — so it does not yet give an atomic "commit the
DB write and publish the event" guarantee. That's the deliberate next
hardening step once a real use case needs it, not something to
speculatively build ahead of need.

⚠️ **Not compiled against the real Quarkus BOM.** This module was
authored without Maven Central access, so while it follows standard
Quarkus/Panache/SmallRye conventions, treat it as a strong starting
point to build and fix up against your actual Quarkus version — not
as verified-working code, unlike `foundation/*` and `identity/domain`
+ `identity/application`, whose tests you can actually run (see
below).

## Architecture rules, enforced by tests, not just by discipline

Every domain/application module has an ArchUnit test that fails the
build if it depends on Quarkus, Hibernate, JPA, JAX-RS, Kafka, Redis,
or Jackson — plus Mutiny, for the two pure-domain modules. Identity's
tests additionally check dependency *direction*:
`IdentityDomainArchitectureTest` fails if `identity.domain` ever
depends on `identity.application`; `IdentityApplicationArchitectureTest`
fails if `identity.application` ever depends on `identity.adapter`.

## Why this stays small (on purpose)

- **No generic `Repository<T, ID>`.** Each bounded context defines its
  own aggregate-specific repository port in *its own*
  `application/port` package — see `UserRepository`. A generic
  repository abstraction fights DDD/ISP/SRP more than it helps.
- **No `TenantId`, `OrganizationId`, `CorrelationId` in the
  foundation.** These aren't universal primitives — they belong to
  Organization/Tenant contexts, and multi-tenancy is a platform
  capability to design deliberately, not bolt onto `Entity`. Note
  that `UserId` *does* now exist — but it lives in
  `identity.domain.user`, not in the foundation, exactly per this
  rule.
- **No `BaseEntity` with `createdAt`/`updatedAt`/`tenantId`/`deleted`.**
  Auditing and tenancy are infrastructure/application concerns that
  get composed in, not inherited by every domain object.
- **No `Result`/`Either` in the domain layer.** Domain methods throw
  `BusinessRuleViolation`/`InvalidStateException` (see
  `UserAlreadyActiveException`); `Result<T>` is an application-layer
  concept for command/query outcomes.
- **`Money` doesn't assume scale = 2.** Rounding/tax policy is a
  separate concern to be designed per accounting context.
- **No transactional outbox yet in `KafkaEventPublisher`.** Real, but
  not needed until a use case demands the stronger guarantee.

## What's intentionally not here yet

- More bounded contexts (`catalog`, `order`, `inventory`, `payment`,
  ...), each following the exact `domain`/`application`/`adapter`
  shape `identity` now demonstrates.
- The `numou-quarkus-platform` starter (shared reactive config, JSON,
  correlation ID, tenant context, security/JWT, observability, Kafka/
  Postgres/Redis conventions, transactional outbox, idempotency
  store, API error contract) — `identity/adapter` hand-wires all of
  this today; a second bounded context would make the duplication
  worth extracting.
- Read-model/projection infrastructure for the CQRS query side beyond
  simple pagination.
- Membership/Role — the piece that will eventually compose tenancy
  and authorization on top of `User` without modifying it.

## Running the tests

```bash
mvn -q test
```

`foundation/*` and `identity/domain` + `identity/application` are
plain JUnit/ArchUnit and will run anywhere with Maven Central access.
`identity/adapter` additionally needs the Quarkus Maven plugin and a
reachable Postgres/Kafka for its own tests (none are included yet —
see "What's intentionally not here yet").

## Next step

Either:

1. Harden `identity/adapter` — compile it against a real Quarkus
   project, add Testcontainers-backed integration tests for
   `PostgresUserRepository` and `KafkaEventPublisher`, add a
   transactional outbox once a use case needs the stronger guarantee.
2. Or start the second bounded context (Catalog or Organization are
   natural next candidates) using `identity/*` as the template —
   that's the point at which shared adapter boilnumoulate becomes worth
   extracting into `numou-quarkus-platform`.
