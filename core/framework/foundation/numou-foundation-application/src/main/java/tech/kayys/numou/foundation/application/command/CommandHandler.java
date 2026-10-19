package tech.kayys.numou.foundation.application.command;

import io.smallrye.mutiny.Uni;

/**
 * Handles exactly one command type and produces a result.
 *
 * This is where reactive orchestration lives (Uni) - the domain
 * aggregates this handler talks to stay plain synchronous Java.
 *
 * @param <C> the command type
 * @param <R> the result type
 */
public interface CommandHandler<C extends Command, R> {

    Uni<R> handle(C command);

}
