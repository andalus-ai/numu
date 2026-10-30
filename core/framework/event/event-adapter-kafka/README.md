# Numou Kafka event publisher adapter

This is an optional infrastructure adapter for the shared `EventPublisherPort`.
It is deliberately a separate Maven module so in-memory and non-Kafka deployments
do not acquire a Kafka runtime dependency.

## Runtime configuration

Add `numou-event-adapter-kafka` to the application that selects distributed
Kafka delivery, then configure the Quarkus outgoing channel:

```properties
mp.messaging.outgoing.numou-events.connector=smallrye-kafka
mp.messaging.outgoing.numou-events.value.serializer=org.apache.kafka.common.serialization.StringSerializer
mp.messaging.outgoing.numou-events.key.serializer=org.apache.kafka.common.serialization.StringSerializer
mp.messaging.outgoing.numou-events.acks=all
mp.messaging.outgoing.numou-events.enable.idempotence=true
mp.messaging.outgoing.numou-events.max.in.flight.requests.per.connection=5
mp.messaging.outgoing.numou-events.delivery.timeout.ms=120000

kafka.bootstrap.servers=${KAFKA_BOOTSTRAP_SERVERS}
numou.events.kafka.topic-prefix=numou.events.
```

The application must also provide exactly one CDI implementation of
`EventSerializerPort`. The adapter does not invent a payload schema or infer
payload classes during deserialization. A serializer must use an explicit,
versioned event-type registry and preserve the complete `EventEnvelope`.

## Delivery semantics

- A record is sent to `<topic-prefix><eventType>`.
- Tenant-scoped events use tenant ID as the Kafka record key, preserving
  per-tenant partition affinity. Platform events use participant ID, then
  event ID as a stable fallback.
- Event ID, event type, envelope version, tenant, participant, actor,
  occurrence time, correlation, causation and trace metadata are emitted
  as Kafka headers when present.
- `publish` completes only after the reactive messaging connector acknowledges
  the record; connector nacks propagate as a failed `Uni`.
- `publishAll` sends sequentially to preserve the ordering contract and stops
  at the first failed send. The outbox remains responsible for retrying.
- The connector's queue admission is not treated as successful delivery.

The selected Kafka channel must use broker acknowledgements and producer
idempotence as shown above. This does not make delivery exactly-once: the POS
outbox remains at-least-once and consumers must use the shared inbox/idempotency
capability.

## Deliberate scope boundary

This module currently implements outbound publication only. A Kafka consumer,
schema registry integration, concrete event serializer, broker-backed
Testcontainers suite, TLS/SASL examples and operational dashboards are separate
follow-up work. Do not enable POS distributed dispatch until the deployment
provides a production serializer and has verified the broker-backed acceptance
tests for its broker and security configuration.
