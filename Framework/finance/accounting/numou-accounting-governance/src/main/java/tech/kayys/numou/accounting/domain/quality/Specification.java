package tech.kayys.syirkah.accounting.domain.quality;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public final class Specification {
    private final SpecificationId id;
    private final String itemCode;
    private final String name;
    private final int version;
    private SpecificationStatus status;
    private final List<SpecificationParameter> parameters = new ArrayList<>();

    public Specification(SpecificationId id, String itemCode, String name, int version) {
        this.id = Objects.requireNonNull(id, "id must not be null");
        this.itemCode = Objects.requireNonNull(itemCode, "itemCode must not be null");
        this.name = Objects.requireNonNull(name, "name must not be null");
        this.version = version;
        this.status = SpecificationStatus.DRAFT;
    }

    public void addParameter(SpecificationParameter param) {
        if (status != SpecificationStatus.DRAFT && status != SpecificationStatus.REVISED) {
            throw new IllegalStateException("Cannot modify parameters in status: " + status);
        }
        parameters.add(Objects.requireNonNull(param, "param must not be null"));
    }

    public void activate() {
        this.status = SpecificationStatus.ACTIVE;
    }

    public void obsolete() {
        this.status = SpecificationStatus.OBSOLETE;
    }

    public SpecificationId id() { return id; }
    public String itemCode() { return itemCode; }
    public String name() { return name; }
    public int version() { return version; }
    public SpecificationStatus status() { return status; }
    public List<SpecificationParameter> parameters() { return List.copyOf(parameters); }
}
