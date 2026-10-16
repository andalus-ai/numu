package tech.kayys.numou.foundation.application.referencedata;

import io.smallrye.mutiny.Uni;
import tech.kayys.numou.foundation.domain.referencedata.ReferenceCode;
import tech.kayys.numou.foundation.domain.referencedata.ReferenceEntryStatus;
import tech.kayys.numou.foundation.domain.tenant.TenantId;

import java.time.LocalDate;
import java.util.Optional;

/**
 * Inbound/Application port for reference code resolution and validation (config03.md §P4-16 #8).
 */
public interface ReferenceDataResolver {

    Uni<Optional<ResolvedReference>> resolve(ReferenceCode code, ReferenceLookupContext context);

    Uni<Boolean> isUsable(ReferenceCode code, ReferenceLookupContext context);

    record ReferenceLookupContext(
            TenantId tenantId,
            LocalDate effectiveDate,
            String purpose
    ) {
        public static ReferenceLookupContext of(TenantId tenantId, LocalDate effectiveDate) {
            return new ReferenceLookupContext(tenantId, effectiveDate, null);
        }
    }

    record ResolvedReference(
            ReferenceCode code,
            String label,
            ReferenceEntryStatus status,
            long version
    ) {}
}
