package tech.kayys.numou.education.pesantren.spi;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletionStage;
import tech.kayys.numou.education.pesantren.domain.DisciplineRecord;
import tech.kayys.numou.education.pesantren.domain.DisciplineRecordId;
import tech.kayys.numou.education.pesantren.domain.SantriId;
import tech.kayys.numou.foundation.domain.repository.Repository;

public interface DisciplineRecordRepository extends Repository<DisciplineRecord, DisciplineRecordId> {
    CompletionStage<List<DisciplineRecord>> findBySantri(SantriId santriId);
}
