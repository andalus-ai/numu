package tech.kayys.numou.construction.spi.design;

import tech.kayys.numou.construction.domain.design.ClashIssue;
import tech.kayys.numou.construction.domain.design.ClashIssueId;
import tech.kayys.numou.foundation.domain.repository.Repository;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletionStage;

public interface ClashIssueRepository extends Repository<ClashIssue, ClashIssueId> {
    CompletionStage<List<ClashIssue>> findByProjectId(UUID projectId);
}
