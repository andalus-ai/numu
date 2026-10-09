package tech.kayys.syirkah.survey.adapter.memory;

import tech.kayys.syirkah.survey.application.port.out.SurveyRepository;
import tech.kayys.syirkah.survey.domain.DomainException;
import tech.kayys.syirkah.survey.domain.ErrorCode;
import tech.kayys.syirkah.survey.domain.id.SurveyId;
import tech.kayys.syirkah.foundation.domain.tenant.TenantId;
import tech.kayys.syirkah.survey.domain.survey.Survey;
import tech.kayys.syirkah.survey.domain.survey.SurveyStatus;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/** Development / test adapter. Data is partitioned per tenant and aggregates are copied in and out. */
public final class InMemorySurveyRepository implements SurveyRepository {
    private final Map<TenantId, Map<SurveyId, Survey>> store = new ConcurrentHashMap<>();

    @Override
    public Optional<Survey> findById(TenantId tenant, SurveyId id) {
        return Optional.ofNullable(store.getOrDefault(tenant, Map.of()).get(id)).map(Survey::copy);
    }

    @Override
    public void save(Survey s) {
        long expected = s.persistedVersion();
        store.computeIfAbsent(s.tenantId(), k -> new ConcurrentHashMap<>()).compute(s.id(), (k, existing) -> {
            long current = existing == null ? 0 : existing.persistedVersion();
            if (current != expected)
                throw new DomainException(ErrorCode.CONFLICT, "Survey " + s.id() + " was modified concurrently");
            Survey stored = s.copy();
            stored.acknowledgePersisted(expected + 1);
            return stored;
        });
        s.acknowledgePersisted(expected + 1);
    }

    @Override
    public List<Survey> findByTenant(TenantId tenant, SurveyStatus status) {
        return store.getOrDefault(tenant, Map.of()).values().stream()
                .filter(s -> status == null || s.status() == status)
                .sorted(Comparator.comparing(Survey::createdAt))
                .map(Survey::copy)
                .toList();
    }

    @Override
    public long countByStatus(TenantId tenant, SurveyStatus status) {
        return store.getOrDefault(tenant, Map.of()).values().stream().filter(s -> s.status() == status).count();
    }
}
