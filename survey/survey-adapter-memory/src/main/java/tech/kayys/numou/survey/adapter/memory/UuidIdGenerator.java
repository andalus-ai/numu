package tech.kayys.numou.survey.adapter.memory;

import tech.kayys.numou.survey.application.port.out.IdGenerator;

import java.util.UUID;

public final class UuidIdGenerator implements IdGenerator {
    @Override public String next() { return UUID.randomUUID().toString(); }
}
