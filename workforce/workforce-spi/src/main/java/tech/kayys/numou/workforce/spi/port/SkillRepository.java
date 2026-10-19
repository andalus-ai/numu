package tech.kayys.numou.workforce.spi.port;

import tech.kayys.numou.foundation.domain.repository.Repository;
import tech.kayys.numou.workforce.domain.skill.Skill;
import tech.kayys.numou.workforce.domain.skill.SkillId;

import java.util.Optional;
import java.util.concurrent.CompletionStage;

/**
 * Persistence port for the {@link Skill} aggregate root.
 */
public interface SkillRepository extends Repository<Skill, SkillId> {

    /**
     * Looks up a skill by its unique code.
     */
    CompletionStage<Optional<Skill>> findByCode(String code);
}
