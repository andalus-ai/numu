package tech.kayys.numou.mobility.ride.domain;

public enum RideStatus {
    SEARCHING, DRIVER_ASSIGNED, DRIVER_ARRIVED, IN_PROGRESS, COMPLETED, CANCELLED, NO_DRIVERS;

    /** A rider may have only one active ride at a time. */
    public boolean isActive() {
        return this == SEARCHING || this == DRIVER_ASSIGNED || this == DRIVER_ARRIVED || this == IN_PROGRESS;
    }
}
