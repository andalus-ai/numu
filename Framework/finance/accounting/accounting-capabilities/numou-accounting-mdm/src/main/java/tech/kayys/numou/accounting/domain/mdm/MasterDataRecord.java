package tech.kayys.syirkah.accounting.domain.mdm;

import java.time.LocalDate;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Universal Master Data entity supporting governance lifecycle and effective dating.
 */
public final class MasterDataRecord {

    private final MasterDataId id;
    private final MasterDataCode code;
    private final MasterDataKind kind;
    private String name;
    private MasterDataStatus status;
    private EffectivePeriod effectivePeriod;
    private int version;
    private final Map<String, String> attributes = new HashMap<>();

    public MasterDataRecord(MasterDataId id, MasterDataCode code, MasterDataKind kind,
                            String name, EffectivePeriod period, Map<String, String> attributes) {
        this.id = Objects.requireNonNull(id);
        this.code = Objects.requireNonNull(code);
        this.kind = Objects.requireNonNull(kind);
        this.name = Objects.requireNonNull(name);
        this.effectivePeriod = Objects.requireNonNull(period);
        this.status = MasterDataStatus.DRAFT;
        this.version = 1;
        if (attributes != null) this.attributes.putAll(attributes);
    }

    public void submitForReview() {
        if (status != MasterDataStatus.DRAFT) throw new IllegalStateException("Only DRAFT can be submitted for review: " + status);
        this.status = MasterDataStatus.REVIEW;
    }

    public void approve() {
        if (status != MasterDataStatus.REVIEW) throw new IllegalStateException("Only in-review records can be approved: " + status);
        this.status = MasterDataStatus.APPROVED;
    }

    public void publish() {
        if (status != MasterDataStatus.APPROVED) throw new IllegalStateException("Only APPROVED records can be published: " + status);
        this.status = MasterDataStatus.PUBLISHED;
    }

    public void retire() {
        this.status = MasterDataStatus.RETIRED;
    }

    public void updateAttributes(Map<String, String> newAttrs, LocalDate effectiveDate) {
        if (status != MasterDataStatus.PUBLISHED && status != MasterDataStatus.DRAFT) {
            throw new IllegalStateException("Cannot update attributes in state: " + status);
        }
        this.attributes.putAll(newAttrs);
        this.version++;
    }

    public boolean isEffectiveAt(LocalDate date) {
        return status == MasterDataStatus.PUBLISHED && effectivePeriod.contains(date);
    }

    public MasterDataId id() { return id; }
    public MasterDataCode code() { return code; }
    public MasterDataKind kind() { return kind; }
    public String name() { return name; }
    public MasterDataStatus status() { return status; }
    public EffectivePeriod effectivePeriod() { return effectivePeriod; }
    public int version() { return version; }
    public Map<String, String> attributes() { return Collections.unmodifiableMap(attributes); }
}
