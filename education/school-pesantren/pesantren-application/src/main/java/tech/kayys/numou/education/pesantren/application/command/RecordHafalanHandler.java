package tech.kayys.numou.education.pesantren.application.command;

import tech.kayys.numou.education.pesantren.domain.HafalanLog;
import tech.kayys.numou.education.pesantren.domain.HafalanLogId;
import tech.kayys.numou.education.pesantren.domain.QuranRange;
import tech.kayys.numou.education.pesantren.spi.HafalanLogRepository;
import tech.kayys.numou.education.pesantren.spi.SantriRepository;
import tech.kayys.numou.foundation.application.result.Result;

import java.util.Objects;
import java.util.concurrent.CompletionStage;

public class RecordHafalanHandler {

    private final SantriRepository santris;
    private final HafalanLogRepository logs;

    public RecordHafalanHandler(SantriRepository santris, HafalanLogRepository logs) {
        this.santris = Objects.requireNonNull(santris);
        this.logs = Objects.requireNonNull(logs);
    }

    public CompletionStage<Result<HafalanLogId>> handle(RecordHafalanCommand cmd) {
        return santris.findById(cmd.santriId()).thenCompose(sOpt -> {
            if (sOpt.isEmpty() || !sOpt.get().getTenantId().equals(cmd.tenantId())) {
                return Outcomes.<HafalanLogId>failed("pesantren.santri.not-found", "Santri not found");
            }
            if (!sOpt.get().isActive()) return Outcomes.<HafalanLogId>failed("pesantren.santri.inactive", "Santri is not active");
            HafalanLog log;
            try {
                log = HafalanLog.record(HafalanLogId.generate(), cmd.tenantId(), cmd.santriId(), cmd.date(), cmd.type(),
                        new QuranRange(cmd.surah(), cmd.fromAyah(), cmd.toAyah()), cmd.result(), cmd.examiner());
            } catch (IllegalArgumentException | NullPointerException e) {
                return Outcomes.<HafalanLogId>failed("pesantren.hafalan.invalid", e.getMessage());
            }
            return logs.save(log).thenApply(saved -> Result.success(saved.getId()));
        });
    }
}
