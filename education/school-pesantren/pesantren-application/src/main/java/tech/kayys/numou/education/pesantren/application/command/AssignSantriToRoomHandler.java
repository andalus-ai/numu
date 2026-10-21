package tech.kayys.numou.education.pesantren.application.command;

import tech.kayys.numou.education.pesantren.domain.DormRoomId;
import tech.kayys.numou.education.pesantren.domain.Residency;
import tech.kayys.numou.education.pesantren.spi.DormitoryRepository;
import tech.kayys.numou.education.pesantren.spi.SantriRepository;
import tech.kayys.numou.foundation.application.result.Result;

import java.util.Objects;
import java.util.concurrent.CompletionStage;

/**
 * Houses an active mukim santri in a dormitory room. The two aggregates are saved one after the other,
 * so a real persistence adapter must run this handler inside a transaction.
 */
public class AssignSantriToRoomHandler {

    private final DormitoryRepository dormitories;
    private final SantriRepository santris;

    public AssignSantriToRoomHandler(DormitoryRepository dormitories, SantriRepository santris) {
        this.dormitories = Objects.requireNonNull(dormitories);
        this.santris = Objects.requireNonNull(santris);
    }

    public CompletionStage<Result<DormRoomId>> handle(AssignSantriToRoomCommand cmd) {
        return dormitories.findById(cmd.dormitoryId()).thenCompose(dOpt -> {
            if (dOpt.isEmpty() || !dOpt.get().getTenantId().equals(cmd.tenantId())) {
                return Outcomes.<DormRoomId>failed("pesantren.dormitory.not-found", "Dormitory not found");
            }
            var dorm = dOpt.get();
            return santris.findById(cmd.santriId()).thenCompose(sOpt -> {
                if (sOpt.isEmpty() || !sOpt.get().getTenantId().equals(cmd.tenantId())) {
                    return Outcomes.<DormRoomId>failed("pesantren.santri.not-found", "Santri not found");
                }
                var santri = sOpt.get();
                if (!santri.getPesantrenId().equals(dorm.getPesantrenId())) {
                    return Outcomes.<DormRoomId>failed("pesantren.santri.other-pesantren", "Santri belongs to a different pesantren");
                }
                // Validate everything on the santri first so a failure cannot leave the dormitory half-updated.
                if (!santri.isActive()) return Outcomes.<DormRoomId>failed("pesantren.santri.inactive", "Santri is not active");
                if (santri.getResidency() != Residency.MUKIM) {
                    return Outcomes.<DormRoomId>failed("pesantren.santri.not-mukim", "Only mukim santri can be housed");
                }
                if (santri.getRoomId() != null) {
                    return Outcomes.<DormRoomId>failed("pesantren.santri.already-housed", "Santri already has a room");
                }
                try {
                    dorm.assign(cmd.roomId(), cmd.santriId(), santri.getGender());
                } catch (IllegalStateException | IllegalArgumentException e) {
                    return Outcomes.<DormRoomId>failed("pesantren.dormitory.cannot-assign", e.getMessage());
                }
                santri.assignRoom(cmd.roomId());
                return dormitories.save(dorm).thenCompose(d -> santris.save(santri))
                        .thenApply(saved -> Result.success(cmd.roomId()));
            });
        });
    }
}
