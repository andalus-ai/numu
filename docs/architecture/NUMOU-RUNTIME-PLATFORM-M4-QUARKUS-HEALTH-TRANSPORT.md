# Numou Runtime Platform — M4 Quarkus Health Transport

**Status:** Implemented as a framework adapter; product integration and CI verification pending  
**Branch:** `runtime/m4-quarkus-health-transport`  
**Base:** M3 bootstrap commit `9405bbaa862fd7ec552b2589fa2b2f8806c06b79`

## Goal

Expose the M3 framework-neutral `ApplicationRuntimeBootstrap` health snapshot through a standard Quarkus REST transport without introducing Quarkus dependencies into the runtime host, plugin contracts, or business capabilities.

## Implementation

The new `core/framework/runtime-quarkus` Maven module publishes `numou-runtime-quarkus` and is included in the `core/framework` reactor.

- `GET /health/live` returns HTTP 200 while the process is live and 503 after it has stopped.
- `GET /health/ready` returns HTTP 200 only in the READY state; all other states return 503.
- Both responses serialize the immutable `RuntimeHealthSnapshot`.
- The adapter requires a product-owned CDI bean for `ApplicationRuntimeBootstrap`.
- The adapter does not construct application manifests, discover plugins, load classes, start the global plugin manager, or select capabilities.

## Boundary

The runtime host remains framework-neutral and owns capability lifecycle. This module owns only Quarkus HTTP transport. Each product owns its trusted composition root, bootstrap lifecycle, and business capability selection.

## Accounting standalone discovery

The existing `finance/accounting/numou-accounting-adapter` is already a Quarkus service module, but its POM and `AccountingServiceProducer` currently depend on or wire Project, Budget, and Inventory components. Therefore it is not yet evidence of a minimal accounting-only distribution. The next accounting application milestone must separate those cross-capability integrations behind explicit optional composition boundaries before claiming that an accounting-only package excludes unrelated capabilities.

## Verification

Focused tests cover STARTING, READY, and STOPPED HTTP status mapping. They are authored but have not been executed in this session. Run:

```bash
cd core/framework
mvn -pl runtime-quarkus -am test
```

Then run the full framework reactor and verify the consuming Quarkus application supplies exactly one `ApplicationRuntimeBootstrap` bean. No build or CI success is claimed.
