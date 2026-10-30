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

## Runtime configuration — inbound consumer

The same optional module provides `EventSubscriberPort` through
`KafkaEventSubscriberAdapter`. It consumes the publisher's event topics using
a Kafka topic regular expression:

```properties
mp.messaging.incoming.numou-events-in.connector=smallrye-kafka
mp.messaging.incoming.numou-events-in.topic=numou\\.events\\..+
mp.messaging.incoming.numou-events-in.pattern=true
mp.messaging.incoming.numou-events-in.value.deserializer=org.apache.kafka.common.serialization.StringDeserializer
mp.messaging.incoming.numou-events-in.key.deserializer=org.apache.kafka.common.serialization.StringDeserializer
mp.messaging.incoming.numou-events-in.group.id=${NUMOU_EVENTS_CONSUMER_GROUP}
mp.messaging.incoming.numou-events-in.enable.auto.commit=false
mp.messaging.incoming.numou-events-in.auto.offset.reset=earliest
```

Use a stable, application-specific consumer group. The topic expression must
match the configured publisher prefix and event-type suffixes. SmallRye Kafka's
`pattern=true` treats `topic` as a regular expression; do not set `topics`
at the same time.

The adapter reads the `numou-event-type` Kafka header and passes it as the
expected type to the versioned `EventSerializerPort`. The application-owned
`EventTypeRegistry` remains the only source of payload classes. Missing
headers, unknown contracts, malformed payloads, and events without a
registered handler fail closed.

Handlers registered for an event type run sequentially. The record is acked
only after all handlers complete. A handler failure nacks the record and the
connector's configured failure strategy governs redelivery or dead-letter
routing. Configure retry limits and a dead-letter topic appropriate to the
deployment; avoid infinite redelivery for poison messages.

The adapter does not provide exactly-once business effects. Each application
must make handlers idempotent and use its own inbox/idempotency boundary where
needed. Inbox markers and business writes must share the same transaction.
The shared transport does not depend on POS persistence or POS inbox tables.

## Deliberate scope boundary

Schema registry integration, broker-backed Testcontainers acceptance tests,
TLS/SASL deployment examples and operational dashboards remain follow-up work.
Do not enable POS distributed dispatch in production until the deployment
provides a production serializer, registers all consumed event contracts,
and verifies broker-backed acceptance tests for its broker and security
configuration.
