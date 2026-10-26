package tech.kayys.numou.hospitality.application;

public final class StayUnavailableException extends RuntimeException {
    public StayUnavailableException() {
        super("The selected accommodation is not available for the requested stay");
    }
}
