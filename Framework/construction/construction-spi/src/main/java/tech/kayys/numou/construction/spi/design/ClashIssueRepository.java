package tech.kayys.syirkah.construction.spi.design;

import tech.kayys.syirkah.construction.domain.design.ClashIssue;
import tech.kayys.syirkah.construction.domain.design.ClashIssueId;
import tech.kayys.syirkah.foundation.domain.repository.Repository;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletionStage;

public interface ClashIssueRepository extends Repository<ClashIssue, ClashIssueId> {
    CompletionStage<List<ClashIssue>> findByProjectId(UUID projectId);
}
