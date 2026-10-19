package tech.kayys.numou.organization.application.port;

import tech.kayys.numou.identity.domain.user.UserId;

import java.util.concurrent.CompletionStage;

public interface IdentityUserPort {

    CompletionStage<Boolean> exists(UserId userId);
}
