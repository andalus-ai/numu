# Numou Phase 1 validation runbook

This starter has two distinct validation layers. Keep their evidence separate:

1. **Static source-contract checks** inspect expected source shapes and run `bash -n` over shell scripts. They do not compile Java.
2. **Disposable PostgreSQL protocol tests** apply the starter's SQL migrations in temporary PostgreSQL containers and exercise database constraints, transaction behavior, tenant predicates, outbox leasing, and idempotency races. They do not call the Java adapters or prove integration with the host application's actual schema.

## Commands

From the `syirkah-phase1` directory:

```bash
# Fast checks; no database or Docker required
./scripts/run-phase1-validation.sh --static-only

# Full starter validation; requires a running Docker daemon and local psql client
./scripts/run-phase1-validation.sh --all

# SQL/container checks only
./scripts/run-phase1-validation.sh --postgres-only
```

The default mode is `--all`. The PostgreSQL scripts create their own named, disposable containers and remove them on exit. They use local loopback ports, configurable with `SYIRKAH_TEST_PG_PORT` for scripts that share a default port. Do not run multiple copies of the suite concurrently with the same port settings.

## Acceptance evidence and limits

Record separately which stages were actually run and their output. A successful static stage is not a successful PostgreSQL stage. A successful SQL protocol stage is not a successful Java integration test.

Before treating Phase 1 as integrated, run the Java tests within the actual Numou checkout and its supported build command, using the real dependency graph and PostgreSQL migrations. Review and reconcile the starter's illustrative/test-only live BOQ tables with the application's real persistence schema. No claim of a full application build, Java test pass, or production readiness should be made from this starter suite alone.

## Troubleshooting

- `Docker CLI is required`: install Docker or run `--static-only`.
- `Docker daemon is not reachable`: start the daemon and retry.
- `local psql client is required`: install a PostgreSQL client compatible with the test containers.
- Port already in use: set `SYIRKAH_TEST_PG_PORT` to a free port for the individual script; avoid parallel runs with overlapping port assignments.
- A SQL test fails: retain the failing script's output and inspect the migration and assertion before changing expected results.
