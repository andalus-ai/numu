package tech.kayys.syirkah.construction.domain.survey;

import tech.kayys.syirkah.foundation.domain.entity.AbstractAggregateRoot;
import java.util.Objects;
import java.util.UUID;

public final class SurveyControlPoint extends AbstractAggregateRoot<SurveyControlPointId> {
    private final UUID siteId;
    private final String pointCode;
    private final double northing;
    private final double easting;
    private final double elevation;
    private SurveyControlPointStatus status;

    private SurveyControlPoint(SurveyControlPointId id, UUID siteId, String pointCode, double northing, double easting, double elevation) {
        super(id);
        this.siteId = Objects.requireNonNull(siteId);
        this.pointCode = Objects.requireNonNull(pointCode);
        this.northing = northing;
        this.easting = easting;
        this.elevation = elevation;
        this.status = SurveyControlPointStatus.ESTABLISHED;
    }

    public static SurveyControlPoint establish(UUID siteId, String pointCode, double northing, double easting, double elevation) {
        return new SurveyControlPoint(SurveyControlPointId.generate(), siteId, pointCode, northing, easting, elevation);
    }

    public void verify() { this.status = SurveyControlPointStatus.VERIFIED; }

    public UUID siteId() { return siteId; }
    public String pointCode() { return pointCode; }
    public double northing() { return northing; }
    public double easting() { return easting; }
    public double elevation() { return elevation; }
    public SurveyControlPointStatus status() { return status; }
}
