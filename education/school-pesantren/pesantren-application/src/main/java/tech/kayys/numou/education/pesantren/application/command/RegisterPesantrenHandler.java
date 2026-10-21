package tech.kayys.numou.education.pesantren.application.command;

import tech.kayys.numou.education.pesantren.domain.Pesantren;
import tech.kayys.numou.education.pesantren.domain.PesantrenId;
import tech.kayys.numou.education.pesantren.spi.PesantrenRepository;
import tech.kayys.numou.foundation.application.result.Result;

import java.util.Objects;
import java.util.concurrent.CompletionStage;

public class RegisterPesantrenHandler {

    private final PesantrenRepository pesantrens;

    public RegisterPesantrenHandler(PesantrenRepository pesantrens) {
        this.pesantrens = Objects.requireNonNull(pesantrens);
    }

    public CompletionStage<Result<PesantrenId>> handle(RegisterPesantrenCommand cmd) {
        Pesantren p;
        try {
            p = Pesantren.register(PesantrenId.generate(), cmd.tenantId(), cmd.name(), cmd.nspp(), cmd.type(), cmd.affiliatedSchool());
        } catch (IllegalArgumentException | NullPointerException e) {
            return Outcomes.failed("pesantren.invalid", e.getMessage());
        }
        return pesantrens.save(p).thenApply(saved -> Result.success(saved.getId()));
    }
}
