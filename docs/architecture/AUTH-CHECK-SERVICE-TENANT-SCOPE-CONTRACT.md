# AuthCheckService — tenant-scoped resource authorization contract

**Status:** Contract proposal for the next identity API revision. The current gRPC implementation does not yet enforce the tenant-binding rules below. Consumers must continue to fail closed until the versioned contract and server-side enforcement are released.

## 1. Purpose and ownership

Numu Identity owns principal authentication, tenant membership, role assignments, effective permissions, and the authoritative allow/deny decision. A consuming capability owns its resource model, resource lifecycle, and the permission vocabulary it requests.

The authorization check is a decision API, not a source of identity supplied by an untrusted caller. A consumer must derive the principal from its own verified authentication context and call Numu over an authenticated service-to-service channel. Numu must authenticate the calling workload and authorize it to request delegated checks for the principal type/ID in the request.

Do not make consumers depend on Identity persistence entities, repositories, or the deployable Quarkus adapter.

## 2. Current API gap

The current `AuthCheckService.CheckPermission` request contains principal type/ID, requested path, and permission. It has no explicit tenant binding. `ResolvePrincipal` returns a tenant ID for tenant-bound JWTs, but the permission-check RPC does not carry or validate that tenant context. The current role-resolution implementation evaluates the requested path; callers must not assume that an arbitrary path is tenant-safe merely because it contains a tenant-like segment.

## 3. Canonical resource path

For tenant-scoped resources, use a canonical, dot-delimited path:

```text
tenant.<tenant-id>.<capability>.<resource-type>.<resource-id>
```

A nested resource appends further typed segments. Example:

```text
tenant.22222222-2222-2222-2222-222222222222.farabi.workspace.aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa.source.orders
```

Rules:

- The first segment is exactly `tenant`; the second segment is the canonical tenant identifier resolved by Numu.
- Segment values must be non-empty and must not contain dots. Consumers must encode opaque identifiers into a single stable path segment; do not concatenate arbitrary display names.
- Authorization inheritance is segment-boundary based. A grant at an ancestor path may apply to descendants; a path prefix without a segment boundary must never match.
- Tenant-neutral resources, if supported, must use a distinct explicit namespace and must never be treated as tenant-scoped by inference.

The tenant prefix is a binding, not a decorative naming convention. For a tenant-bound principal, Numu must reject a request when the explicit tenant ID does not equal the tenant bound to the verified principal. For a principal with membership in multiple tenants, Numu must verify active membership in the requested tenant before evaluating grants. The tenant segment in the requested path must equal the explicit tenant ID.

## 4. Permission vocabulary

Permission names remain consumer-owned, stable, lowercase dotted identifiers. Numu stores and evaluates them as opaque exact strings; it must not infer application behavior from a permission name.

A consumer should define permissions around user-visible actions and use least privilege. For a source-management capability, an example vocabulary is:

| Permission | Meaning |
|---|---|
| `farabi.source.register` | Create a source definition in a workspace |
| `farabi.source.view` | Read source configuration and status |
| `farabi.source.test` | Open a governed test connection |
| `farabi.source.discover` | Discover catalogs, schemas, datasets and columns |
| `farabi.source.query` | Execute governed read queries |
| `farabi.source.delete` | Remove a source definition |

The consumer must choose the correct authorization path for each action. Collection/workspace actions (such as register) target the workspace's source collection path; instance actions (view, test, discover, query, delete) target the individual source path. Do not authorize an instance action only at a broader tenant root.

## 5. API evolution requirements

The current protobuf API must be extracted from `numou-identity-adapter` into a separately versioned, reusable contract artifact before external consumers integrate it. The artifact must own the canonical `.proto` source and generated client/server API. The deployable Identity adapter and external consumers depend on this artifact; consumers must not copy the schema or depend on the deployable adapter.

The next compatible API revision must:

- Add an explicit tenant binding to single and bulk permission checks. Existing field numbers and meanings must not be changed or reused.
- Validate the tenant binding against the authenticated principal/membership and the requested path before resolving effective permissions.
- Return a structured decision outcome suitable for distinguishing deny from unavailable/invalid request at the transport boundary, while preserving fail-closed behavior.
- Bound path length, permission length, batch size, and request processing time.
- Authenticate service callers (mTLS or equivalent workload identity) and restrict which workloads may request delegated authorization for which principal types.
- Preserve compatibility for existing callers during migration, but do not silently treat legacy tenant-unbound checks as safe for new tenant-scoped consumers.

The contract artifact should use semantic versioning. Additive protobuf fields are compatible; changing field meaning, enum meaning, or authorization semantics requires a major contract revision and migration plan.

## 6. Required server-side decision order

For every check, Numu must:

1. Authenticate the calling workload.
2. Validate request shape, principal type/identifier, permission, and resource path.
3. Resolve or validate the principal under the caller's delegated-check policy; do not trust a caller-supplied principal identifier solely because it is syntactically valid.
4. Verify the explicit tenant against the principal's tenant binding or active tenant membership.
5. Verify the requested path tenant segment equals the explicit tenant.
6. Resolve effective permissions at the exact requested path using segment-boundary inheritance.
7. Return allow only when the exact required permission is present; every validation, membership, dependency, or evaluation failure must deny.

Bulk checks must apply the same validation independently to every path and preserve result ordering. A malformed item must not cause an unrelated item to be granted.

## 7. Consumer integration requirements

Consumers must:

- Derive principal type/ID and tenant/workspace from verified server-side authentication context, never request-body fields or unverified headers.
- Resolve workspace ownership/membership through the platform's authoritative tenant/workspace context; a workspace identifier alone is not proof of access.
- Build resource paths from validated identifiers and the canonical path builder; never accept a caller-supplied authorization path.
- Call Numu before resource lookup, metadata discovery, external connection opening, or query execution where existence disclosure or side effects are possible.
- Fail closed on timeout, unavailable Numu, invalid response, identity mismatch, or unknown outcome.
- Keep authorization checks in the application/runtime boundary. Domain ports may express the action but must not depend on gRPC or Numu implementation classes.
- Test that denied operations perform no registry lookup, metadata call, secret resolution, connection open, or query execution.

## 8. Acceptance criteria before production adoption

- A standalone versioned contract artifact is published and consumed by the Identity adapter and at least one external client without schema copying.
- Tests prove tenant mismatch denial, cross-tenant membership denial, exact permission matching, ancestor inheritance, segment-boundary isolation, and bulk result ordering.
- Tests prove an unauthenticated or unauthorized workload cannot request checks for arbitrary principal IDs.
- Tests prove invalid paths, oversized batches, timeouts, and unavailable authorization dependencies fail closed.
- A migration plan covers legacy tenant-unbound permission-check callers.
- The consumer demonstrates that its principal and tenant values originate from verified server-side identity, and that denial happens before resource lookup or side effects.

Until these criteria are met, the existing API is an internal implementation detail for current consumers, not a production-ready cross-product authorization contract.
