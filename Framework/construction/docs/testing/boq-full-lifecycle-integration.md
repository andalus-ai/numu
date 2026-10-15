# BOQ full lifecycle integration scenario

`DefaultPgBoqRevisionOperationsFactoryIntegrationTest.fullLifecycleEmitsOneEventPerTransitionAndRejectsSelfApproval` describes the expected PostgreSQL-backed flow:

1. Capture the draft BOQ into revision 1 and emit exactly one `BoqRevisionCaptured` outbox event.
2. Submit revision 1 for review and emit exactly one `BoqRevisionSubmittedForReview` event.
3. Reject approval by the submitting actor without writing an approval event.
4. Allow a different actor to approve revision 1, transition the live BOQ and snapshot to `APPROVED`, and emit exactly one `BoqRevisionApproved` event.

The test uses the bundle's test fixture tables for live BOQ data. The revision snapshot, counter, and outbox tables must be provisioned by the project migrations. The source bundle does not include the full Syirkah repository build configuration, so Java compilation and execution against a configured PostgreSQL test database remain to be run in the full repository.

## Local static checks

From the `syirkah-phase1` directory, run:

```bash
./scripts/run-phase1-static-checks.sh
```

This executes every `check-*.sh` source-contract script and runs `bash -n` on all shell scripts. It is not a substitute for Java compilation or live PostgreSQL integration tests.
