package tech.kayys.numou.identity.domain.serviceaccount;

import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertThrows;

class ServiceAccountTest {

    @Test
    void rejectsDescriptionLongerThanDatabaseColumnLimit() {
        String description = "x".repeat(256);

        assertThrows(IllegalArgumentException.class, () -> ServiceAccount.create(
                ServiceAccountId.newId(),
                null,
                "integration-agent",
                description,
                null,
                Instant.parse("2026-10-28T00:00:00Z")));
    }
}
