package tech.kayys.numou.foundation.application.result;

import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;

class ResultTest {

    @Test
    void wrapsSuccessValue() {
        var result = Result.success("order-created");

        assertTrue(result.isSuccess());
        assertFalse(result.isFailure());
        assertEquals("order-created", result.orElseThrow());
        assertTrue(result.toOptional().isPresent());
        assertEquals("order-created", result.toOptional().get());
        assertTrue(result.failureError().isEmpty());
    }

    @Test
    void wrapsFailureError() {
        var error = ApplicationError.of("ORDER_NOT_FOUND", "Order does not exist");
        var result = Result.<String>failure(error);

        assertTrue(result.isFailure());
        assertFalse(result.isSuccess());
        assertTrue(result.toOptional().isEmpty());
        assertTrue(result.failureError().isPresent());
        assertEquals(error, result.failureError().get());

        var thrown = assertThrows(
                ApplicationErrorException.class,
                result::orElseThrow
        );

        assertEquals(error, thrown.error());
    }

    @Test
    void mapsSuccessValue() {
        var result = Result.success(2).map(value -> value * 10);

        assertEquals(20, result.orElseThrow());
    }

    @Test
    void mapPropagatesFailureUnchanged() {
        var error = ApplicationError.of("INVALID", "Invalid state");
        Result<Integer> result = Result.failure(error);

        var mapped = result.map(value -> value * 10);

        assertTrue(mapped.isFailure());
        assertEquals(error, mapped.failureError().orElseThrow());
    }

    @Test
    void flatMapsSuccessValue() {
        var result = Result.success(5)
                .flatMap(val -> Result.success(val * 2));

        assertTrue(result.isSuccess());
        assertEquals(10, result.orElseThrow());
    }

    @Test
    void flatMapReturnsFailureWhenNestedFails() {
        var error = ApplicationErrors.notFound("Item", 123);
        var result = Result.success(5)
                .<Integer>flatMap(val -> Result.failure(error));

        assertTrue(result.isFailure());
        assertEquals(error, result.failureError().orElseThrow());
    }

    @Test
    void recoversFromFailure() {
        var error = ApplicationErrors.conflict("Already exists");
        var result = Result.<String>failure(error)
                .recover(err -> Result.success("recovered"));

        assertTrue(result.isSuccess());
        assertEquals("recovered", result.orElseThrow());
    }

    @Test
    void foldsSuccessAndFailure() {
        var successResult = Result.success(100);
        var foldedSuccess = successResult.fold(val -> "Val: " + val, err -> "Err: " + err.code());
        assertEquals("Val: 100", foldedSuccess);

        var failureResult = Result.<Integer>failure(ApplicationErrors.unauthorized("Bad token"));
        var foldedFailure = failureResult.fold(val -> "Val: " + val, err -> "Err: " + err.code());
        assertEquals("Err: UNAUTHORIZED", foldedFailure);
    }

    @Test
    void executesCallbacks() {
        var successHook = new AtomicBoolean(false);
        Result.success("ok").onSuccess(val -> successHook.set(true));
        assertTrue(successHook.get());

        var failureHook = new AtomicBoolean(false);
        Result.failure(ApplicationErrors.validation("Invalid")).onFailure(err -> failureHook.set(true));
        assertTrue(failureHook.get());
    }
}
