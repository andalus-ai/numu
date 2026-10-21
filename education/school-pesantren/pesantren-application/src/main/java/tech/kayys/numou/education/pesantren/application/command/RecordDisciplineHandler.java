package tech.kayys.numou.education.pesantren.application.command;

import tech.kayys.numou.education.pesantren.domain.DisciplineRecord;
import tech.kayys.numou.education.pesantren.domain.DisciplineRecordId;
import tech.kayys.numou.education.pesantren.spi.DisciplineRecordRepository;
import tech.kayys.numou.education.pesantren.spi.SantriRepository;
import tech.kayys.numou.foundation.application.result.Result;

import java.util.Objects;
import java.util.concurrent.CompletionStage;

public class RecordDisciplineHandler {

    private final SantriRepository santris;
    private final DisciplineRecordRepository records;

    public RecordDisciplineHandler(SantriRepository santris, DisciplineRecordRepository records) {
        this.santris = Objects.requireNonNull(santris);
        this.records = Objects.requireNonNull(records);
    }

    public CompletionStage<Result<DisciplineRecordId>> handle(RecordDisciplineCommand cmd) {
        return santris.findById(cmd.santriId()).thenCompose(sOpt -> {
            if (sOpt.isEmpty() || !sOpt.get().getTenantId().equals(cmd.tenantId())) {
                return Outcomes.<DisciplineRecordId>failed("pesantren.santri.not-found", "Santri not found");
            }
            DisciplineRecord r;
            try {
                r = DisciplineRecord.record(DisciplineRecordId.generate(), cmd.tenantId(), cmd.santriId(), cmd.date(),
                        cmd.category(), cmd.description(), cmd.points(), cmd.recordedBy());
            } catch (IllegalArgumentException | NullPointerException e) {
                return Outcomes.<DisciplineRecordId>failed("pesantren.discipline.invalid", e.getMessage());
            }
            return records.save(r).thenApply(saved -> Result.success(saved.getId()));
        });
    }
}
