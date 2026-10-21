package tech.kayys.numou.education.pesantren.application.command;

import tech.kayys.numou.education.pesantren.domain.HalaqahId;
import tech.kayys.numou.education.pesantren.spi.HalaqahRepository;
import tech.kayys.numou.education.pesantren.spi.SantriRepository;
import tech.kayys.numou.foundation.application.result.Result;

import java.util.Objects;
import java.util.concurrent.CompletionStage;

public class JoinHalaqahHandler {

    private final HalaqahRepository halaqahs;
    private final SantriRepository santris;

    public JoinHalaqahHandler(HalaqahRepository halaqahs, SantriRepository santris) {
        this.halaqahs = Objects.requireNonNull(halaqahs);
        this.santris = Objects.requireNonNull(santris);
    }

    public CompletionStage<Result<HalaqahId>> handle(JoinHalaqahCommand cmd) {
        return halaqahs.findById(cmd.halaqahId()).thenCompose(hOpt -> {
            if (hOpt.isEmpty() || !hOpt.get().getTenantId().equals(cmd.tenantId())) {
                return Outcomes.<HalaqahId>failed("pesantren.halaqah.not-found", "Halaqah not found");
            }
            var halaqah = hOpt.get();
            return santris.findById(cmd.santriId()).thenCompose(sOpt -> {
                if (sOpt.isEmpty() || !sOpt.get().getTenantId().equals(cmd.tenantId())) {
                    return Outcomes.<HalaqahId>failed("pesantren.santri.not-found", "Santri not found");
                }
                if (!sOpt.get().isActive()) return Outcomes.<HalaqahId>failed("pesantren.santri.inactive", "Santri is not active");
                if (!sOpt.get().getPesantrenId().equals(halaqah.getPesantrenId())) {
                    return Outcomes.<HalaqahId>failed("pesantren.santri.other-pesantren", "Santri belongs to a different pesantren");
                }
                try {
                    halaqah.join(cmd.santriId());
                } catch (IllegalStateException e) {
                    return Outcomes.<HalaqahId>failed("pesantren.halaqah.cannot-join", e.getMessage());
                }
                return halaqahs.save(halaqah).thenApply(saved -> Result.success(saved.getId()));
            });
        });
    }
}
