package tech.kayys.syirkah.identity.adapter.bootstrap;

import jakarta.enterprise.context.ApplicationScoped;
import tech.kayys.syirkah.foundation.domain.time.DomainClock;

import java.time.Instant;

/**
 * The one and only place Instant.now() is called for this service.
 * Every handler depends on DomainClock, never on Instant.now()
 * directly - that's what lets FixedDomainClock make time-dependent
 * behavior deterministic in tests.
 */
@ApplicationScoped
public class SystemDomainClock implements DomainClock {

    @Override
    public Instant now() {
        return Instant.now();
    }

}
