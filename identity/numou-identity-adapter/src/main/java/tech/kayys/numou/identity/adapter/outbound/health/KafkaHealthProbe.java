package tech.kayys.numou.identity.adapter.outbound.health;

import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.AdminClientConfig;
import org.apache.kafka.clients.admin.ListTopicsOptions;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import tech.kayys.numou.observability.HealthProbe;
import tech.kayys.numou.observability.HealthStatus;

import java.util.Properties;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * Readiness probe for the Kafka cluster the audit stream publishes to: asks the broker for its
 * topic list with a short timeout. Runs the (blocking) admin call on a dedicated daemon thread
 * so the event loop is never blocked by a slow/unreachable broker.
 */
@ApplicationScoped
public class KafkaHealthProbe implements HealthProbe {

    private static final int TIMEOUT_MS = 2_000;

    private final String bootstrapServers;
    private final ExecutorService executor = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "identity-kafka-health");
        thread.setDaemon(true);
        return thread;
    });

    @Inject
    public KafkaHealthProbe(
            @ConfigProperty(
                    name = "mp.messaging.outgoing.identity-audit-log.bootstrap.servers",
                    defaultValue = "localhost:9092"
            ) String bootstrapServers
    ) {
        this.bootstrapServers = bootstrapServers;
    }

    @Override
    public String name() {
        return "kafka";
    }

    @Override
    public Uni<HealthStatus> probe() {
        return Uni.createFrom().<HealthStatus>item(this::check)
                .runSubscriptionOn(executor);
    }

    private HealthStatus check() {
        Properties properties = new Properties();
        properties.put(AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        properties.put(AdminClientConfig.REQUEST_TIMEOUT_MS_CONFIG, TIMEOUT_MS);
        properties.put(AdminClientConfig.DEFAULT_API_TIMEOUT_MS_CONFIG, TIMEOUT_MS);
        properties.put(AdminClientConfig.CLIENT_ID_CONFIG, "identity-health-probe");

        try (AdminClient admin = AdminClient.create(properties)) {
            admin.listTopics(new ListTopicsOptions().timeoutMs(TIMEOUT_MS))
                    .names()
                    .get(TIMEOUT_MS + 1_000L, TimeUnit.MILLISECONDS);
            return HealthStatus.up(name());
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            return HealthStatus.down(name(), "interrupted while checking broker");
        } catch (Exception ex) {
            return HealthStatus.down(name(), ex.getClass().getSimpleName());
        }
    }
}
