package tech.kayys.numou.education.pesantren.application.command;

import tech.kayys.numou.education.pesantren.domain.Santri;
import tech.kayys.numou.education.pesantren.domain.SantriId;
import tech.kayys.numou.education.pesantren.spi.PesantrenRepository;
import tech.kayys.numou.education.pesantren.spi.SantriRepository;
import tech.kayys.numou.foundation.application.result.Result;

import java.util.Objects;
import java.util.concurrent.CompletionStage;

public class RegisterSantriHandler {

    private final PesantrenRepository pesantrens;
    private final SantriRepository santris;

    public RegisterSantriHandler(PesantrenRepository pesantrens, SantriRepository santris) {
        this.pesantrens = Objects.requireNonNull(pesantrens);
        this.santris = Objects.requireNonNull(santris);
    }

    public CompletionStage<Result<SantriId>> handle(RegisterSantriCommand cmd) {
        return pesantrens.findById(cmd.pesantrenId()).thenCompose(p -> {
            if (p.isEmpty() || !p.get().getTenantId().equals(cmd.tenantId())) {
                return Outcomes.<SantriId>failed("pesantren.not-found", "Pesantren not found");
            }
            if (cmd.santriNumber() == null) {
                return Outcomes.<SantriId>failed("pesantren.santri.invalid", "santriNumber must not be blank");
            }
            return santris.findByNumber(cmd.tenantId(), cmd.santriNumber().trim()).thenCompose(existing -> {
                if (existing.isPresent()) {
                    return Outcomes.<SantriId>failed("pesantren.santri.duplicate-number", "Santri number is already used");
                }
                Santri santri;
                try {
                    santri = Santri.register(SantriId.generate(), cmd.tenantId(), cmd.pesantrenId(), cmd.santriNumber(),
                            cmd.fullName(), cmd.gender(), cmd.birthDate(), cmd.residency(),
                            cmd.guardianName(), cmd.guardianPhone());
                } catch (IllegalArgumentException | NullPointerException e) {
                    return Outcomes.<SantriId>failed("pesantren.santri.invalid", e.getMessage());
                }
                return santris.save(santri).thenApply(saved -> Result.success(saved.getId()));
            });
        });
    }
}
