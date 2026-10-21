package tech.kayys.numou.education.pesantren.application.command;

import tech.kayys.numou.education.pesantren.domain.Halaqah;
import tech.kayys.numou.education.pesantren.domain.HalaqahId;
import tech.kayys.numou.education.pesantren.spi.HalaqahRepository;
import tech.kayys.numou.education.pesantren.spi.PesantrenRepository;
import tech.kayys.numou.foundation.application.result.Result;

import java.util.Objects;
import java.util.concurrent.CompletionStage;

public class CreateHalaqahHandler {

    private final PesantrenRepository pesantrens;
    private final HalaqahRepository halaqahs;

    public CreateHalaqahHandler(PesantrenRepository pesantrens, HalaqahRepository halaqahs) {
        this.pesantrens = Objects.requireNonNull(pesantrens);
        this.halaqahs = Objects.requireNonNull(halaqahs);
    }

    public CompletionStage<Result<HalaqahId>> handle(CreateHalaqahCommand cmd) {
        return pesantrens.findById(cmd.pesantrenId()).thenCompose(p -> {
            if (p.isEmpty() || !p.get().getTenantId().equals(cmd.tenantId())) {
                return Outcomes.<HalaqahId>failed("pesantren.not-found", "Pesantren not found");
            }
            Halaqah h;
            try {
                h = Halaqah.create(HalaqahId.generate(), cmd.tenantId(), cmd.pesantrenId(), cmd.name(), cmd.kitabTitle(),
                        cmd.method(), cmd.ustadz(), cmd.day(), cmd.startTime(), cmd.capacity());
            } catch (IllegalArgumentException | NullPointerException e) {
                return Outcomes.<HalaqahId>failed("pesantren.halaqah.invalid", e.getMessage());
            }
            return halaqahs.save(h).thenApply(saved -> Result.success(saved.getId()));
        });
    }
}
