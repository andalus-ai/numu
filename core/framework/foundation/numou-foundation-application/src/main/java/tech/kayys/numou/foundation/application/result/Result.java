package tech.kayys.numou.foundation.application.result;

import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * Explicit success/failure outcome for a use case, instead of relying
 * on exceptions for expected/business failures (Docs/Enhancement/enhance04.md).
 *
 * <p>A {@code Result} represents an expected application outcome; exceptions represent
 * abnormal execution.
 *
 * @param <T> the success value type
 */
public sealed interface Result<T> permits Result.Success, Result.Failure {

    static <T> Result<T> success(T value) {
        return new Success<>(value);
    }

    static <T> Result<T> failure(ApplicationError error) {
        return new Failure<>(error);
    }

    boolean isSuccess();

    default boolean isFailure() {
        return !isSuccess();
    }

    /**
     * Converts this result to an {@link Optional} containing the value if successful,
     * or empty if this is a failure.
     */
    default Optional<T> toOptional() {
        return isSuccess() ? Optional.of(orElseThrow()) : Optional.empty();
    }

    /**
     * Returns an Optional containing the error if this is a failure, or empty if successful.
     */
    default Optional<ApplicationError> failureError() {
        return isFailure() ? Optional.of(((Failure<T>) this).error()) : Optional.empty();
    }

    /**
     * Returns the success value, or throws {@link ApplicationErrorException}
     * if this is a failure.
     */
    T orElseThrow();

    /**
     * Maps the success value using the provided function.
     */
    <R> Result<R> map(Function<? super T, ? extends R> mapper);

    /**
     * Flat-maps the success value into another {@link Result}.
     */
    <R> Result<R> flatMap(Function<? super T, Result<R>> mapper);

    /**
     * Recovers from a failure using the provided fallback function.
     */
    Result<T> recover(Function<ApplicationError, Result<T>> recovery);

    /**
     * Folds the result into a single value by applying either the success or failure transformation.
     */
    <R> R fold(Function<? super T, ? extends R> onSuccess, Function<ApplicationError, ? extends R> onFailure);

    /**
     * Performs a side effect on the success value without altering the result.
     */
    default Result<T> peek(Consumer<? super T> consumer) {
        return onSuccess(consumer);
    }

    /**
     * Executes the given consumer if this result is successful.
     */
    Result<T> onSuccess(Consumer<? super T> consumer);

    /**
     * Executes the given consumer if this result is a failure.
     */
    Result<T> onFailure(Consumer<ApplicationError> consumer);

    record Success<T>(T value) implements Result<T> {

        public Success {
            Objects.requireNonNull(value, "value cannot be null");
        }

        @Override
        public boolean isSuccess() {
            return true;
        }

        @Override
        public T orElseThrow() {
            return value;
        }

        @Override
        public <R> Result<R> map(Function<? super T, ? extends R> mapper) {
            Objects.requireNonNull(mapper, "mapper cannot be null");
            return new Success<>(mapper.apply(value));
        }

        @Override
        public <R> Result<R> flatMap(Function<? super T, Result<R>> mapper) {
            Objects.requireNonNull(mapper, "mapper cannot be null");
            return Objects.requireNonNull(mapper.apply(value), "mapped result cannot be null");
        }

        @Override
        public Result<T> recover(Function<ApplicationError, Result<T>> recovery) {
            return this;
        }

        @Override
        public <R> R fold(Function<? super T, ? extends R> onSuccess, Function<ApplicationError, ? extends R> onFailure) {
            Objects.requireNonNull(onSuccess, "onSuccess cannot be null");
            return onSuccess.apply(value);
        }

        @Override
        public Result<T> onSuccess(Consumer<? super T> consumer) {
            Objects.requireNonNull(consumer, "consumer cannot be null");
            consumer.accept(value);
            return this;
        }

        @Override
        public Result<T> onFailure(Consumer<ApplicationError> consumer) {
            return this;
        }

    }

    record Failure<T>(ApplicationError error) implements Result<T> {

        public Failure {
            Objects.requireNonNull(error, "error cannot be null");
        }

        @Override
        public boolean isSuccess() {
            return false;
        }

        @Override
        public T orElseThrow() {
            throw error.toException();
        }

        @SuppressWarnings("unchecked")
        @Override
        public <R> Result<R> map(Function<? super T, ? extends R> mapper) {
            return (Result<R>) this;
        }

        @SuppressWarnings("unchecked")
        @Override
        public <R> Result<R> flatMap(Function<? super T, Result<R>> mapper) {
            return (Result<R>) this;
        }

        @Override
        public Result<T> recover(Function<ApplicationError, Result<T>> recovery) {
            Objects.requireNonNull(recovery, "recovery cannot be null");
            return Objects.requireNonNull(recovery.apply(error), "recovery result cannot be null");
        }

        @Override
        public <R> R fold(Function<? super T, ? extends R> onSuccess, Function<ApplicationError, ? extends R> onFailure) {
            Objects.requireNonNull(onFailure, "onFailure cannot be null");
            return onFailure.apply(error);
        }

        @Override
        public Result<T> onSuccess(Consumer<? super T> consumer) {
            return this;
        }

        @Override
        public Result<T> onFailure(Consumer<ApplicationError> consumer) {
            Objects.requireNonNull(consumer, "consumer cannot be null");
            consumer.accept(error);
            return this;
        }

    }

}
