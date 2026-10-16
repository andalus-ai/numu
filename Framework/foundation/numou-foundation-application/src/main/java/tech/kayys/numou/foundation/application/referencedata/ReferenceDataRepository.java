package tech.kayys.numou.foundation.application.referencedata;

import io.smallrye.mutiny.Uni;
import tech.kayys.numou.foundation.domain.referencedata.ReferenceCode;
import tech.kayys.numou.foundation.domain.referencedata.ReferenceEntry;
import tech.kayys.numou.foundation.domain.referencedata.ReferenceSetDefinition;

import java.util.List;
import java.util.Optional;

/**
 * Outbound repository port for reference data storage (config03.md §P4-16 #3).
 */
public interface ReferenceDataRepository {

    Uni<Optional<ReferenceSetDefinition>> findSetByKey(String setKey);

    Uni<Optional<ReferenceEntry>> findEntryByCode(ReferenceCode code);

    Uni<List<ReferenceEntry>> findEntriesBySetKey(String setKey);

    Uni<ReferenceEntry> saveEntry(ReferenceEntry entry);
}
