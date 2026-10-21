package tech.kayys.numou.education.pesantren.application.command;

import tech.kayys.numou.education.pesantren.domain.LeavePermit;
import tech.kayys.numou.education.pesantren.domain.LeavePermitId;
import tech.kayys.numou.education.pesantren.domain.Residency;
import tech.kayys.numou.education.pesantren.spi.LeavePermitRepository;
import tech.kayys.numou.education.pesantren.spi.SantriRepository;
import tech.kayys.numou.foundation.application.result.Result;

import java.util.Objects;
import java.util.concurrent.CompletionStage;

/** Only active mukim santri need a permit to leave; kalong santri go home daily. */
public class RequestLeavePermitHandler {

    private final SantriRepository santris;
    private final LeavePermitRepository permits;

    public RequestLeavePermitHandler(SantriRepository santris, LeavePermitRepository permits) {
        this.santris = Objects.requireNonNull(santris);
        this.permits = Objects.requireNonNull(permits);
    }

    public CompletionStage<Result<LeavePermitId>> handle(RequestLeavePermitCommand cmd) {
        return santris.findById(cmd.santriId()).thenCompose(sOpt -> {
            if (sOpt.isEmpty() || !sOpt.get().getTenantId().equals(cmd.tenantId())) {
                return Outcomes.<LeavePermitId>failed("pesantren.santri.not-found", "Santri not found");
            }
            var santri = sOpt.get();
            if (!santri.isActive()) return Outcomes.<LeavePermitId>failed("pesantren.santri.inactive", "Santri is not active");
            if (santri.getResidency() != Residency.MUKIM) {
                return Outcomes.<LeavePermitId>failed("pesantren.leave.not-mukim", "Only mukim santri need a leave permit");
            }
            LeavePermit permit;
            try {
                permit = LeavePermit.request(LeavePermitId.generate(), cmd.tenantId(), cmd.santriId(), cmd.from(), cmd.to(), cmd.reason());
            } catch (IllegalArgumentException | NullPointerException e) {
                return Outcomes.<LeavePermitId>failed("pesantren.leave.invalid", e.getMessage());
            }
            return permits.save(permit).thenApply(saved -> Result.success(saved.getId()));
        });
    }
}
