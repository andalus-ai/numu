# Transport M5 — Runtime operations for hold expiry

## Goal

Make M4's expiration use case operational in a Quarkus host without making the Transport domain or application service depend on Quarkus.

## Delivered

- Add an optional `transport-runtime-quarkus` module, kept separate from domain, application, and JDBC adapter modules.
- Compose default CDI beans for the PostgreSQL inventory repository, UTC clock, and booking application service. Host applications can replace the default beans to customize composition.
- Add a bounded scheduled worker with local overlap prevention, configurable interval/batch size, startup validation, structured failure logging, and Micrometer counters/timer.
- Keep tenant identifiers out of metrics to avoid data leakage and unbounded cardinality.
- Document host datasource/migration responsibilities, configuration, multi-replica behavior, metrics, and operational boundaries.

## Operational semantics

The scheduler processes at most one configured candidate batch per tick; a full batch continues on the next tick rather than draining an unbounded backlog in one invocation. Quarkus prevents overlapping executions within one process. Across replicas, optimistic persistence and re-evaluation—not a distributed scheduler lock—provide safety. Repeated ticks are expected and safe.

Expired holds only release capacity according to the Transport lifecycle. They do not imply a payment refund, ticket cancellation, or customer notification. Those effects need explicit cross-capability contracts and durable delivery.

## Deliberately out of scope

- Public HTTP booking API and tenant authorization.
- Payment authorization/capture/refund and ticket issuance.
- Durable domain-event outbox integration.
- Distributed scheduler lease or backlog-specific health endpoint.

These are separate concerns and should not be inferred from the existence of a scheduled worker.

## Verification

Run `mvn -B -ntp -f transport/pom.xml verify` on Java 21. CI must confirm compilation of the optional Quarkus module as well as the domain and PostgreSQL integration tests. PostgreSQL Testcontainers tests must actually run (not be skipped) for full adapter verification.
