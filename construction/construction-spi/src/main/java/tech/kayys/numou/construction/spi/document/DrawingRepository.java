package tech.kayys.numou.construction.spi.document;

import tech.kayys.numou.construction.domain.document.Drawing;
import tech.kayys.numou.construction.domain.document.DrawingId;
import tech.kayys.numou.foundation.domain.repository.Repository;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletionStage;

public interface DrawingRepository extends Repository<Drawing, DrawingId> {
    CompletionStage<List<Drawing>> findByProjectId(UUID projectId);
}
