package tech.kayys.syirkah.construction.spi.document;

import tech.kayys.syirkah.construction.domain.document.Drawing;
import tech.kayys.syirkah.construction.domain.document.DrawingId;
import tech.kayys.syirkah.foundation.domain.repository.Repository;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletionStage;

public interface DrawingRepository extends Repository<Drawing, DrawingId> {
    CompletionStage<List<Drawing>> findByProjectId(UUID projectId);
}
