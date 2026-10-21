package tech.kayys.numou.education.school.application.command;

import tech.kayys.numou.foundation.application.result.ApplicationError;
import tech.kayys.numou.foundation.application.result.Result;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

/** Small helpers for building failed use-case outcomes. */
final class Outcomes {
    private Outcomes() {}

    static <T> Result<T> fail(String code, String message) {
        return Result.failure(ApplicationError.of(code, message));
    }

    static <T> CompletionStage<Result<T>> failed(String code, String message) {
        return CompletableFuture.completedFuture(fail(code, message));
    }
}
