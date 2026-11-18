package tech.kayys.numou.talent.application.port;

import io.smallrye.mutiny.Uni;
import tech.kayys.numou.foundation.application.context.ExecutionContext;

import java.util.function.Function;

/** Executes a profile mutation and its outbox/event append in one transaction. */
public interface TalentProfileTransactionRunner {
    <T> Uni<T> inTransaction(
            ExecutionContext context,
            Function<TalentProfileTransaction, Uni<T>> work
    );
}
