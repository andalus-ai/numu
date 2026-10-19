package tech.kayys.numou.asset.infrastructure.time;

import jakarta.enterprise.context.ApplicationScoped;
import tech.kayys.numou.foundation.domain.time.DomainClock;

import java.time.Instant;

/** Production {@link DomainClock} backed by the system clock. */
@ApplicationScoped
public class SystemDomainClock implements DomainClock {

    @Override
    public Instant now() {
        return Instant.now();
    }
}
