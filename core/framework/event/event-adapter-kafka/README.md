# Numou Kafka event adapter

This is an optional infrastructure adapter for the shared event publisher and subscriber ports.
It is deliberately a separate Maven module so in-memory and non-Kafka deployments do not
acquire a Kafka runtime dependency.

## Runtime configuration

Add `numou-event-adapter-kafka` to the application that selects distributed Kafka delivery.

Outgoing channel:

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

Incoming channel:

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

Use a stable, application-specific consumer group. The topic expression must match the
publisher prefix and event-type suffixes. With `pattern=true`, `topic` is interpreted as
a regular expression; do not also configure `topics`.

The application must provide exactly one CDI `EventSerializerPort` implementation using
an explicit, versioned event-type registry. The adapter does not infer payload classes.

## Delivery semantics

- Events are routed to `<topic-prefix><eventType>`.
- Tenant-scoped events use tenant ID as the Kafka record key; platform events use
  participant ID and then event ID as a stable fallback.
- Event ID, event type, envelope version and available tenant/participant/actor/time/
  correlation/causation/trace metadata are emitted as Kafka headers.
- Publisher completion waits for connector acknowledgement. Batch publishing is sequential.
- Consumer handlers for one event type run sequentially. The record is acked only after
  every matching handler succeeds; malformed or unhandled records fail closed.
- Handler failures nack the record. Configure finite retry limits and dead-letter routing
  in the consuming application; this transport adapter does not invent a retry policy.
- Delivery is at-least-once. Consumers must be idempotent and use the shared inbox or an
  equivalent business idempotency boundary. Inbox markers and business writes should
  share one transaction.

## Verification boundaries

`KafkaEventPublisherBrokerTest` uses Testcontainers to verify publisher topic, key, payload,
and contract headers against a real Kafka broker.

`KafkaEventSubscriberAdapterTest` verifies manual ack/nack behavior, sequential handler
execution, malformed-record rejection, and safe handling of an ack failure with controlled
message metadata. It simulates a second delivery to verify handler retryability.

`KafkaConsumerConnectorTest` runs the Quarkus incoming connector against a Testcontainers
Kafka broker. It verifies real record consumption/handler dispatch, full event-envelope metadata
preservation (tenant, participant, actor, correlation and trace context), delivery to two
independent consumer groups, and connector-managed dead-letter routing for malformed payloads.
Its offset-recovery case uses Kafka consumers with a stable group ID against the same real broker:
it simulates a process stop before commit, verifies redelivery after restart, commits the offset,
and verifies a subsequent restart does not replay the committed record. This isolates broker
consumer-group offset semantics; it does not claim that a Quarkus process restart itself was
orchestrated or that handler side effects and Kafka offsets are one atomic transaction. It does not
claim exactly-once delivery or prove application-level retry policy; deployments must select
bounded retry and DLQ settings that match their operational requirements.

Run the module tests from `core/framework`:

```bash
mvn -pl event/event-adapter-kafka -am test
```

Do not enable POS distributed dispatch in production until a deployment supplies its
production serializer, registers all event contracts, and verifies its broker, security,
retry, and dead-letter configuration end to end.
