package tech.kayys.syirkah.reservation.domain;

import java.time.LocalTime;

/**
 * Compatibility alias for OperatingWindow in restaurant / dining domains.
 */
public final class ServicePeriod {
    private final OperatingWindow window;

    public ServicePeriod(String name, LocalTime opens, LocalTime lastSeating) {
        this.window = new OperatingWindow(name, opens, lastSeating, lastSeating);
    }

    public ServicePeriod(String name, LocalTime opens, LocalTime closes, LocalTime lastSeating) {
        this.window = new OperatingWindow(name, opens, closes, lastSeating);
    }

    public OperatingWindow toOperatingWindow() {
        return window;
    }

    public String name() {
        return window.name();
    }

    public LocalTime opens() {
        return window.opens();
    }

    public LocalTime closes() {
        return window.closes();
    }

    public LocalTime lastSeating() {
        return window.lastSeating();
    }
}
