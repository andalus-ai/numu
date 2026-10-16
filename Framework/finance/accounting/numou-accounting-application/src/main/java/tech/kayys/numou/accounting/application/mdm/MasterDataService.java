package tech.kayys.syirkah.accounting.application.mdm;

import tech.kayys.syirkah.accounting.domain.mdm.*;

import java.time.LocalDate;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Application service managing master data registries and golden records.
 */
public final class MasterDataService {

    private final Map<MasterDataId, MasterDataRecord> records = new ConcurrentHashMap<>();

    public MasterDataRecord register(MasterDataId id, MasterDataCode code, MasterDataKind kind,
                                     String name, LocalDate validFrom, Map<String, String> attrs) {
        MasterDataRecord record = new MasterDataRecord(
                id, code, kind, name, EffectivePeriod.openEnded(validFrom), attrs);
        records.put(record.id(), record);
        return record;
    }

    public MasterDataRecord publish(MasterDataId id) {
        MasterDataRecord record = get(id);
        record.submitForReview();
        record.approve();
        record.publish();
        return record;
    }

    public Optional<MasterDataRecord> findEffective(MasterDataCode code, LocalDate asOfDate) {
        return records.values().stream()
                .filter(r -> r.code().equals(code) && r.isEffectiveAt(asOfDate))
                .findFirst();
    }

    public List<MasterDataRecord> findByTaxNumber(String taxNumber) {
        return records.values().stream()
                .filter(r -> taxNumber.equals(r.attributes().get("taxNumber")))
                .toList();
    }

    public MasterDataRecord get(MasterDataId id) {
        MasterDataRecord r = records.get(id);
        if (r == null) throw new IllegalArgumentException("MasterDataRecord not found: " + id.value());
        return r;
    }

    public List<MasterDataRecord> listAll() { return List.copyOf(records.values()); }
}
