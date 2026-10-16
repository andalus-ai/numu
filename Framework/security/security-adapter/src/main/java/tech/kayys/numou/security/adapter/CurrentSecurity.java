package tech.kayys.syirkah.security.adapter;

import jakarta.enterprise.context.RequestScoped;
import tech.kayys.syirkah.security.application.context.SecurityContext;

/** The verified {@link SecurityContext} of the current request; empty until the security filter has run. */
@RequestScoped
public class CurrentSecurity {

    private SecurityContext context;

    void set(SecurityContext context) {
        this.context = context;
    }

    /** @throws IllegalStateException if called outside an authenticated, tenant-resolved request */
    public SecurityContext require() {
        if (context == null) {
            throw new IllegalStateException("No authenticated security context on this request");
        }
        return context;
    }
}
