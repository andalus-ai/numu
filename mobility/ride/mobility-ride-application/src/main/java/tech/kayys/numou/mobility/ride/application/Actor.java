package tech.kayys.numou.mobility.ride.application;

import java.util.UUID;

/** The authenticated caller, as the application layer needs it for ownership checks. */
public record Actor(UUID id, Role role) {

    public enum Role { RIDER, DRIVER, OPS }

    public static Actor rider(UUID id) { return new Actor(id, Role.RIDER); }

    public static Actor driver(UUID id) { return new Actor(id, Role.DRIVER); }

    public static Actor ops(UUID id) { return new Actor(id, Role.OPS); }
}
