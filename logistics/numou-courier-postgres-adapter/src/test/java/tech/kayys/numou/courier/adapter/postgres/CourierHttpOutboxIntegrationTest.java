package tech.kayys.numou.courier.adapter.postgres;

import com.sun.net.httpserver.HttpServer;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.postgresql.ds.PGSimpleDataSource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import javax.sql.DataSource;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

@Testcontainers(disabledWithoutDocker = true)
class CourierHttpOutboxIntegrationTest {
    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("numou_courier")
            .withUsername("courier")
            .withPassword("courier");

    static DataSource dataSource;
    static HttpServer server;
    static final AtomicInteger responseStatus = new AtomicInteger(503);
    static final AtomicInteger requests = new AtomicInteger();
    static final AtomicReference<String> requestBody = new AtomicReference<>();
    static final AtomicReference<String> idempotencyKey = new AtomicReference<>();
    static final AtomicReference<String> authorization = new AtomicReference<>();

    @BeforeAll
    static void startInfrastructure() throws Exception {
        Flyway.configure()
                .dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
                .locations("classpath:db/migration")
                .load().migrate();

        PGSimpleDataSource source = new PGSimpleDataSource();
        source.setURL(POSTGRES.getJdbcUrl());
        source.setUser(POSTGRES.getUsername());
        source.setPassword(POSTGRES.getPassword());
        dataSource = source;

        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/events", exchange -> {
            try {
                requestBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
                idempotencyKey.set(exchange.getRequestHeaders().getFirst("Idempotency-Key"));
                authorization.set(exchange.getRequestHeaders().getFirst("Authorization"));
                requests.incrementAndGet();
                int status = responseStatus.get();
                exchange.sendResponseHeaders(status, -1);
            } finally {
                exchange.close();
            }
        });
        server.start();
    }

    @AfterAll
    static void stopInfrastructure() {
        if (server != null) server.stop(0);
    }

    @Test
    void realRelayRetriesHttpFailureAndAcknowledgesOnlySuccessfulGatewayResponse() throws Exception {
        UUID tenantId = UUID.randomUUID();
        UUID eventId = UUID.randomUUID();
        String destination = "http-m30";
        insertEvent(tenantId, eventId, destination);

        URI endpoint = URI.create("http://127.0.0.1:" + server.getAddress().getPort() + "/events");
        HttpCourierEventPublisher http = new HttpCourierEventPublisher(
                endpoint, destination, "integration-secret", 1000, 2000,
                HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(1)).build());
        JdbcCourierOutboxPublisher relay = new JdbcCourierOutboxPublisher(dataSource);

        // The first HTTP response is a gateway failure: relay must not acknowledge it.
        assertEquals(0, relay.publishBatch(destination, "worker-http-first", 10, http));
        assertEquals("PENDING", publicationValue(tenantId, eventId, destination, "status"));
        assertEquals(1, integerValue(tenantId, eventId, destination, "attempt_count"));
        assertEquals(tenantId + ":" + eventId, idempotencyKey.get());
        assertTrue(requestBody.get().contains("\"eventId\":\"" + eventId + "\""));

        // Make the retry eligible immediately and let the same HTTP gateway accept it.
        responseStatus.set(204);
        execute("UPDATE courier_event_publications SET available_at = now() WHERE tenant_id = ? AND event_id = ? AND destination = ?",
                tenantId, eventId, destination);

        assertEquals(1, relay.publishBatch(destination, "worker-http-retry", 10, http));
        assertEquals("PUBLISHED", publicationValue(tenantId, eventId, destination, "status"));
        assertEquals(2, integerValue(tenantId, eventId, destination, "attempt_count"));
        assertEquals(2, requests.get());
        assertEquals(tenantId + ":" + eventId, idempotencyKey.get());
        assertTrue(requestBody.get().contains("\"tenantId\":\"" + tenantId + "\""));
        assertTrue(requestBody.get().contains("\"payload\":{\"shipmentId\":\"M30\"}"));
        assertEquals("Bearer integration-secret", authorization.get());
    }

    private static void insertEvent(UUID tenantId, UUID eventId, String destination) throws Exception {
        try (Connection c = dataSource.getConnection(); PreparedStatement event = c.prepareStatement("""
                INSERT INTO courier_event_outbox
                  (tenant_id, id, event_type, aggregate_type, aggregate_reference, occurred_at, payload)
                VALUES (?, ?, 'courier.shipment.delivered.v1', 'shipment', ?, now(), CAST(? AS jsonb))
                """)) {
            event.setObject(1, tenantId);
            event.setObject(2, eventId);
            event.setString(3, "SHIP-" + eventId);
            event.setString(4, "{\"shipmentId\":\"M30\"}");
            assertEquals(1, event.executeUpdate());
        }
        try (Connection c = dataSource.getConnection(); PreparedStatement publication = c.prepareStatement("""
                INSERT INTO courier_event_publications (tenant_id, event_id, destination)
                VALUES (?, ?, ?)
                """)) {
            publication.setObject(1, tenantId);
            publication.setObject(2, eventId);
            publication.setString(3, destination);
            assertEquals(1, publication.executeUpdate());
        }
    }

    private static String publicationValue(UUID tenantId, UUID eventId, String destination, String column) throws Exception {
        if (!column.matches("[a-z_]+")) throw new IllegalArgumentException("invalid column");
        try (Connection c = dataSource.getConnection(); PreparedStatement query = c.prepareStatement(
                "SELECT " + column + " FROM courier_event_publications WHERE tenant_id = ? AND event_id = ? AND destination = ?")) {
            bind(query, tenantId, eventId, destination);
            try (ResultSet rs = query.executeQuery()) {
                assertTrue(rs.next());
                Object value = rs.getObject(1);
                return value == null ? null : value.toString();
            }
        }
    }

    private static int integerValue(UUID tenantId, UUID eventId, String destination, String column) throws Exception {
        return Integer.parseInt(publicationValue(tenantId, eventId, destination, column));
    }

    private static void execute(String sql, UUID tenantId, UUID eventId, String destination) throws Exception {
        try (Connection c = dataSource.getConnection(); PreparedStatement update = c.prepareStatement(sql)) {
            bind(update, tenantId, eventId, destination);
            assertEquals(1, update.executeUpdate());
        }
    }

    private static void bind(PreparedStatement statement, UUID tenantId, UUID eventId, String destination) throws Exception {
        statement.setObject(1, tenantId);
        statement.setObject(2, eventId);
        statement.setString(3, destination);
    }
}
