package tech.kayys.numou.foundation.application.referencedata;

import io.smallrye.mutiny.Uni;
import tech.kayys.numou.foundation.domain.referencedata.ReferenceCode;
import tech.kayys.numou.foundation.domain.referencedata.ReferenceEntry;

import java.time.LocalDate;
import java.util.Objects;
import java.util.Optional;

/**
 * Default implementation of {@link ReferenceDataResolver}.
 */
public class DefaultReferenceDataResolver implements ReferenceDataResolver {

    private final ReferenceDataRepository repository;

    public DefaultReferenceDataResolver(ReferenceDataRepository repository) {
        this.repository = Objects.requireNonNull(repository, "repository cannot be null");
    }

    @Override
    public Uni<Optional<ResolvedReference>> resolve(ReferenceCode code, ReferenceLookupContext context) {
        LocalDate date = context != null && context.effectiveDate() != null ? context.effectiveDate() : LocalDate.now();

        return repository.findEntryByCode(code)
                .map(maybeEntry -> maybeEntry
                        .filter(entry -> entry.isEffective(date))
                        .map(this::toResolved));
    }

    @Override
    public Uni<Boolean> isUsable(ReferenceCode code, ReferenceLookupContext context) {
        LocalDate date = context != null && context.effectiveDate() != null ? context.effectiveDate() : LocalDate.now();

        return repository.findEntryByCode(code)
                .map(maybeEntry -> maybeEntry.map(entry -> entry.isUsable(date)).orElse(false));
    }

    private ResolvedReference toResolved(ReferenceEntry entry) {
        return new ResolvedReference(
                entry.code(),
                entry.label(),
                entry.status(),
                entry.version()
        );
    }
}
