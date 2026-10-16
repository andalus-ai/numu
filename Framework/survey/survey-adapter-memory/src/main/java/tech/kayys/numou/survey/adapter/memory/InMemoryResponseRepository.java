package tech.kayys.numou.survey.adapter.memory;

import tech.kayys.numou.survey.application.port.out.ResponseRepository;
import tech.kayys.numou.survey.domain.DomainException;
import tech.kayys.numou.survey.domain.ErrorCode;
import tech.kayys.numou.survey.domain.id.ResponseId;
import tech.kayys.numou.survey.domain.id.SurveyId;
import tech.kayys.numou.foundation.domain.tenant.TenantId;
import tech.kayys.numou.survey.domain.response.Response;
import tech.kayys.numou.survey.domain.response.ResponseStatus;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

public final class InMemoryResponseRepository implements ResponseRepository {
    private final Map<TenantId, Map<ResponseId, Response>> store = new ConcurrentHashMap<>();

    @Override
    public Optional<Response> findById(TenantId tenant, ResponseId id) {
        return Optional.ofNullable(store.getOrDefault(tenant, Map.of()).get(id)).map(Response::copy);
    }

    @Override
    public void save(Response r) {
        long expected = r.persistedVersion();
        store.computeIfAbsent(r.tenantId(), k -> new ConcurrentHashMap<>()).compute(r.id(), (k, existing) -> {
            long current = existing == null ? 0 : existing.persistedVersion();
            if (current != expected)
                throw new DomainException(ErrorCode.CONFLICT, "Response " + r.id() + " was modified concurrently");
            Response stored = r.copy();
            stored.acknowledgePersisted(expected + 1);
            return stored;
        });
        r.acknowledgePersisted(expected + 1);
    }

    @Override
    public List<Response> findSubmitted(TenantId tenant, SurveyId survey) {
        return store.getOrDefault(tenant, Map.of()).values().stream()
                .filter(r -> r.surveyId().equals(survey) && r.status() == ResponseStatus.SUBMITTED)
                .map(Response::copy).toList();
    }

    @Override
    public long countSubmitted(TenantId tenant, SurveyId survey) {
        return store.getOrDefault(tenant, Map.of()).values().stream()
                .filter(r -> r.surveyId().equals(survey) && r.status() == ResponseStatus.SUBMITTED).count();
    }

    @Override
    public long countByRespondent(TenantId tenant, SurveyId survey, String respondentId) {
        return store.getOrDefault(tenant, Map.of()).values().stream()
                .filter(r -> r.surveyId().equals(survey) && r.respondentId().filter(respondentId::equals).isPresent())
                .count();
    }
}
