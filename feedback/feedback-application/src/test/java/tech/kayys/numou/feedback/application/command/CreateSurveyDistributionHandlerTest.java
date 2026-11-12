package tech.kayys.numou.feedback.application.command;

import org.junit.jupiter.api.Test;
import tech.kayys.numou.feedback.application.port.SurveyDistributionRepository;
import tech.kayys.numou.feedback.application.query.ResolveSurveyDistributionHandler;
import tech.kayys.numou.feedback.domain.survey.*;
import tech.kayys.numou.foundation.domain.tenant.TenantId;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class CreateSurveyDistributionHandlerTest {
    @Test
    void issuesOpaqueTokenAndResolverUsesOnlyItsHash() {
        var repository = new InMemoryDistributionRepository();
        var handler = new CreateSurveyDistributionHandler(repository);
        var now = Instant.parse("2099-01-01T12:00:00Z");

        var created = handler.handle(new CreateSurveyDistributionCommand(TenantId.generate(),
                "survey-123", DistributionChannel.PUBLIC_LINK, now.plusSeconds(3600), 50, now));

        assertNotNull(created.rawToken());
        assertEquals(43, created.rawToken().length());
        assertNotEquals(created.rawToken(), created.distribution().tokenHash());
        assertEquals(64, created.distribution().tokenHash().length());
        assertTrue(new ResolveSurveyDistributionHandler(repository).handle(created.rawToken()).isPresent());
        assertTrue(new ResolveSurveyDistributionHandler(repository).handle("wrong-token").isEmpty());
    }

    private static final class InMemoryDistributionRepository implements SurveyDistributionRepository {
        private SurveyDistribution saved;

        @Override
        public void save(SurveyDistribution distribution) {
            saved = distribution;
        }

        @Override
        public Optional<SurveyDistribution> findById(TenantId tenantId, UUID id) {
            if (saved == null || !saved.tenantId().equals(tenantId) || !saved.id().equals(id)) return Optional.empty();
            return Optional.of(saved);
        }

        @Override
        public Optional<SurveyDistribution> findActiveByTokenHash(String tokenHash) {
            if (saved == null || !saved.tokenHash().equals(tokenHash)
                    || saved.status() != DistributionStatus.ACTIVE
                    || !Instant.now().isBefore(saved.expiresAt())) return Optional.empty();
            return Optional.of(saved);
        }
    }
}
