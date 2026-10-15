# Outbox relay service unit tests

## Coverage added

`OutboxRelayServiceTest` uses a deterministic clock and fake store/delivery port to cover the application orchestration boundary:

1. An empty claim returns zero and does not call delivery.
2. Successful delivery acknowledges using the claimed event's lease token and the injected clock.
3. Delivery failure schedules capped-policy retry time, keeps the event non-terminal below the attempt threshold, and bounds persisted error text to 2,000 characters.
4. An exhausted attempt is marked terminal and uses the failure timestamp instead of scheduling another retry.
5. A failing event does not prevent an independent event in the same batch from being processed.
6. Invalid lease duration, batch size, and worker identity are rejected by the service constructor.

Run the static source-presence check with:

```bash
bash scripts/check-outbox-relay-service-test-contract.sh
```

Run these tests through the actual Syirkah Java test task once the source is placed in the real module. This source bundle has no Maven/Gradle descriptor or complete dependency graph, so these tests have **not** been compiled or executed here. They use fakes and do not verify PostgreSQL SQL, scheduler registration, broker integration, metrics, or production CDI wiring.
