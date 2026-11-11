package tech.kayys.numou.attendee.application.query;

import io.smallrye.mutiny.Uni;
import tech.kayys.numou.attendee.application.port.AttendeeAuthorizationPort;
import tech.kayys.numou.attendee.application.port.AttendeeProfileRepository;
import tech.kayys.numou.attendee.application.port.AttendeeTenantContextProvider;
import tech.kayys.numou.attendee.domain.AttendeeProfile;
import tech.kayys.numou.foundation.application.query.QueryHandler;

import java.util.Objects;
import java.util.Optional;

/** Reads a profile only inside the authenticated tenant boundary. */
public final class GetAttendeeProfileHandler
        implements QueryHandler<GetAttendeeProfileQuery, Optional<AttendeeProfile>> {

    private final AttendeeProfileRepository profiles;
    private final AttendeeTenantContextProvider tenantContext;
    private final AttendeeAuthorizationPort authorization;

    public GetAttendeeProfileHandler(
            AttendeeProfileRepository profiles,
            AttendeeTenantContextProvider tenantContext,
            AttendeeAuthorizationPort authorization) {
        this.profiles = Objects.requireNonNull(profiles, "profiles cannot be null");
        this.tenantContext = Objects.requireNonNull(tenantContext, "tenantContext cannot be null");
        this.authorization = Objects.requireNonNull(authorization, "authorization cannot be null");
    }

    @Override
    public Uni<Optional<AttendeeProfile>> handle(GetAttendeeProfileQuery query) {
        Objects.requireNonNull(query, "query cannot be null");
        var context = tenantContext.current();
        return authorization.require(new AttendeeAuthorizationPort.AuthorizationRequest(
                        context.tenantId(), context.actorId(), AttendeeAuthorizationPort.Action.VIEW_PROFILE))
                .flatMap(ignored -> profiles.findByTenantAndId(context.tenantId(), query.profileId()));
    }
}
