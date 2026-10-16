package tech.kayys.syirkah.organization.application.port;

import tech.kayys.syirkah.identity.domain.user.UserId;

import java.util.concurrent.CompletionStage;

public interface IdentityUserPort {

    CompletionStage<Boolean> exists(UserId userId);
}
