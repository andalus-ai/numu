package tech.kayys.numou.attendee.application.port;

/** Supplies the authenticated actor and verified active tenant. */
@FunctionalInterface
public interface AttendeeTenantContextProvider {
    AttendeeTenantContext current();
}
