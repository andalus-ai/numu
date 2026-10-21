package tech.kayys.numou.mobility.api;

import tech.kayys.numou.mobility.ride.application.Actor;
import tech.kayys.numou.security.application.context.SecurityContext;

import java.util.UUID;

/** Maps the verified security context to the application layer's {@link Actor}. The id is the token's user id (never a request value). */
final class Actors {

    private Actors() { }

    static UUID id(SecurityContext ctx) {
        return ctx.principal().id().value();
    }

    static Actor rider(SecurityContext ctx) {
        return Actor.rider(id(ctx));
    }

    static Actor driver(SecurityContext ctx) {
        return Actor.driver(id(ctx));
    }

    static Actor ops(SecurityContext ctx) {
        return Actor.ops(id(ctx));
    }
}
