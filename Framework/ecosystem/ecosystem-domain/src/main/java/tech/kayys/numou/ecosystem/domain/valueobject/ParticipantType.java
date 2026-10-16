package tech.kayys.syirkah.ecosystem.domain.valueobject;

import tech.kayys.syirkah.foundation.domain.valueobject.ValueObject;

/**
 * How a participant relates to the Numou platform.
 *
 * <p>Deliberately keeps "provider" and "the provider's software" separate:
 * a {@link #TENANT} may run its own FMS/ERP and still act as a provider to
 * other tenants; a {@link #SYIRKAH_SERVICE} is capability hosted by the
 * platform itself; an {@link #EXTERNAL_PROVIDER} never migrates but
 * integrates through the platform contracts.
 */
public enum ParticipantType implements ValueObject {

    /** A company operating inside Numou (uses at least one capability). */
    TENANT,

    /** A capability operated by Numou on behalf of the ecosystem. */
    SYIRKAH_SERVICE,

    /** A third party integrated through API/webhook/event contracts only. */
    EXTERNAL_PROVIDER,

    /** An individual actor: independent driver, courier, field technician. */
    INDIVIDUAL,

    /** The platform itself, as a participant of last resort. */
    PLATFORM
}
