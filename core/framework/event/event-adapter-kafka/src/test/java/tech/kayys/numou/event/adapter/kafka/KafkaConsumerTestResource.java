package tech.kayys.numou.event.adapter.kafka;

import io.quarkus.test.common.QuarkusTestResourceLifecycleManager;
import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.NewTopic;
import org.testcontainers.containers.KafkaContainer;
import org.testcontainers.utility.DockerImageName;

import java.util.List;
import java.util.Map;

/** Starts an isolated Kafka broker and creates pattern-subscribed topics before Quarkus starts. */
public final class KafkaConsumerTestResource implements QuarkusTestResourceLifecycleManager {

    private KafkaContainer kafka;

    @Override
    public Map<String, String> start() {
        kafka = new KafkaContainer(DockerImageName.parse("confluentinc/cp-kafka:7.7.0"));
        kafka.start();
        try (AdminClient admin = AdminClient.create(Map.of(
                "bootstrap.servers", kafka.getBootstrapServers()))) {
            admin.createTopics(List.of(
                    new NewTopic("numou.events.pos.sale.completed", 1, (short) 1),
                    new NewTopic("numou.events.dead-letter", 1, (short) 1),
                    new NewTopic("numou-events", 1, (short) 1)))
                    .all()
                    .get();
        } catch (Exception e) {
            kafka.stop();
            throw new IllegalStateException("Unable to create Kafka consumer acceptance-test topics", e);
        }
        return Map.of("kafka.bootstrap.servers", kafka.getBootstrapServers());
    }

    @Override
    public void stop() {
        if (kafka != null) {
            kafka.stop();
        }
    }
}
