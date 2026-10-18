package tech.kayys.numou.survey.application.port.out;

/** Generates unique identifier values used by survey application use cases. */
@FunctionalInterface
public interface IdGenerator {
    String next();
}
