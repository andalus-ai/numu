package tech.kayys.numou.numourest;

import org.junit.jupiter.api.Test;
import tech.kayys.numou.foundation.domain.exception.BusinessRuleViolation;
import tech.kayys.numou.foundation.domain.exception.InvalidStateException;
import tech.kayys.numou.numouapplication.port.SyirkahRepository;
import tech.kayys.numou.numoudomain.SyirkahNotFoundException;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SyirkahExceptionMapperTest {

    // Response is a stub-free abstraction only under a JAX-RS runtime, so the status mapping is tested through
    // the pure classifier.
    @Test
    void classifiesFailures() {
        assertEquals(404, SyirkahExceptionMapper.statusOf(new SyirkahNotFoundException("x")));
        assertEquals(422, SyirkahExceptionMapper.statusOf(new BusinessRuleViolation("rule")));
        assertEquals(409, SyirkahExceptionMapper.statusOf(new BusinessRuleViolation("approval already APPROVED")));
        assertEquals(409, SyirkahExceptionMapper.statusOf(new IllegalStateException("decided")));
        assertEquals(409, SyirkahExceptionMapper.statusOf(new SyirkahRepository.ConcurrentModificationException("v")));
        assertEquals(400, SyirkahExceptionMapper.statusOf(new IllegalArgumentException("bad")));
        assertEquals(403, SyirkahExceptionMapper.statusOf(new SecurityException("no")));
        assertEquals(500, SyirkahExceptionMapper.statusOf(new RuntimeException("boom")));
    }
}
