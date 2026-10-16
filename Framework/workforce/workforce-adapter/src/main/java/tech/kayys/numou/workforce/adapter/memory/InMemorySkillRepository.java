package tech.kayys.numou.workforce.adapter.memory;

import tech.kayys.numou.workforce.domain.skill.Skill;
import tech.kayys.numou.workforce.domain.skill.SkillId;
import tech.kayys.numou.workforce.spi.port.SkillRepository;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;

public final class InMemorySkillRepository implements SkillRepository {

    private final Map<SkillId, Skill> skillsById = new ConcurrentHashMap<>();
    private final Map<String, SkillId> idsByCode = new ConcurrentHashMap<>();

    @Override
    public CompletionStage<Skill> save(Skill skill) {
        Objects.requireNonNull(skill, "skill cannot be null");
        skillsById.put(skill.id(), skill);
        idsByCode.put(skill.getCode(), skill.id());
        return CompletableFuture.completedFuture(skill);
    }

    @Override
    public CompletionStage<Optional<Skill>> findById(SkillId id) {
        return CompletableFuture.completedFuture(Optional.ofNullable(skillsById.get(id)));
    }

    @Override
    public CompletionStage<Optional<Skill>> findByCode(String code) {
        var id = idsByCode.get(code);
        return CompletableFuture.completedFuture(
                id == null ? Optional.empty() : Optional.ofNullable(skillsById.get(id))
        );
    }

    @Override
    public CompletionStage<Boolean> existsById(SkillId id) {
        return CompletableFuture.completedFuture(skillsById.containsKey(id));
    }

    @Override
    public CompletionStage<Void> delete(Skill aggregate) {
        Objects.requireNonNull(aggregate, "skill cannot be null");
        skillsById.remove(aggregate.id());
        idsByCode.remove(aggregate.getCode());
        return CompletableFuture.completedFuture(null);
    }

    @Override
    public CompletionStage<Void> deleteById(SkillId id) {
        var removed = skillsById.remove(id);
        if (removed != null) {
            idsByCode.remove(removed.getCode());
        }
        return CompletableFuture.completedFuture(null);
    }
}
