package tech.kayys.numou.mobility.kernel.domain;

/** Vehicle classes a rider can request and a driver can serve. A driver serves exactly the type they are registered for. */
public enum VehicleType {
    BIKE(1, 120.0),
    CAR(4, 200.0),
    CAR_XL(6, 200.0);

    private final int seats;
    private final double maxPlausibleKmh;

    VehicleType(int seats, double maxPlausibleKmh) {
        this.seats = seats;
        this.maxPlausibleKmh = maxPlausibleKmh;
    }

    public int seats() {
        return seats;
    }

    /** Above this speed between two GPS fixes the move is treated as a glitch or spoof, not travel. */
    public double maxPlausibleKmh() {
        return maxPlausibleKmh;
    }
}
