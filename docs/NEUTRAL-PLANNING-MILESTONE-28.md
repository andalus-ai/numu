# Milestone 28 — Schedule execution reliability and scope provisioning

## Scope

This milestone makes the tenant/structure scope prerequisite from M27 provisionable through the application layer, and adds PostgreSQL integration coverage for concurrent execution and rollback.

## Ownership provisioning

- `RegisterSchedulePlanScope` is the application use case for registering an existing plan to an authoritative tenant and structure.
- `SchedulePlanScopeStore` is the persistence port.
- Registration is idempotent for the same owner. Reassigning a plan to another tenant or structure must fail rather than silently changing ownership.
- The use case does not itself authenticate callers. API/application entry points must authorize the caller and verify tenant membership before invoking it.

## Reliability tests

- Two executions approved against the same base revision race against PostgreSQL. Exactly one may apply; the other must persist a conflict receipt.
- If replacing a schedule would delete an activity referenced by append-only progress evidence, PostgreSQL rejects the write. The plan revision, working schedule, and execution receipt must all remain unchanged.

## Verification status

Tests are authored but have not been executed in this session. CI must compile the application and adapter modules and run the PostgreSQL integration tests before the milestone can be considered verified.
