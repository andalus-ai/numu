package tech.kayys.numou.attendee.application.command;

import io.smallrye.mutiny.Uni;
import tech.kayys.numou.attendee.application.port.AttendeeAuthorizationPort;
import tech.kayys.numou.attendee.application.port.AttendeeProfileRepository;
import tech.kayys.numou.attendee.application.port.AttendeeTenantContextProvider;
import tech.kayys.numou.attendee.domain.AttendeeProfile;
import tech.kayys.numou.ecosystem.application.port.PartyLookupPort;
import tech.kayys.numou.ecosystem.domain.party.PartyId;
import tech.kayys.numou.foundation.application.command.CommandHandler;
import tech.kayys.numou.foundation.application.result.ApplicationError;
import tech.kayys.numou.foundation.application.result.Result;
import tech.kayys.numou.foundation.application.transaction.UnitOfWork;

import java.time.Clock;
import java.util.Objects;
import java.util.UUID;

/** Creates an attendee profile without accepting tenant or actor identity from the caller. */
public final class CreateAttendeeProfileHandler implements
        CommandHandler<CreateAttendeeProfileCommand, Result<CreateAttendeeProfileResult>> {

    private final AttendeeProfileRepository profiles;
    private final PartyLookupPort parties;
    private final AttendeeTenantContextProvider tenantContext;
    private final AttendeeAuthorizationPort authorization;
    private final UnitOfWork unitOfWork;
    private final Clock clock;

    public CreateAttendeeProfileHandler(
            AttendeeProfileRepository profiles,
            PartyLookupPort parties,
            AttendeeTenantContextProvider tenantContext,
            AttendeeAuthorizationPort authorization,
            UnitOfWork unitOfWork,
            Clock clock) {
        this.profiles = Objects.requireNonNull(profiles, "profiles cannot be null");
        this.parties = Objects.requireNonNull(parties, "parties cannot be null");
        this.tenantContext = Objects.requireNonNull(tenantContext, "tenantContext cannot be null");
        this.authorization = Objects.requireNonNull(authorization, "authorization cannot be null");
        this.unitOfWork = Objects.requireNonNull(unitOfWork, "unitOfWork cannot be null");
        this.clock = Objects.requireNonNull(clock, "clock cannot be null");
    }

    @Override
    public Uni<Result<CreateAttendeeProfileResult>> handle(CreateAttendeeProfileCommand command) {
        Objects.requireNonNull(command, "command cannot be null");
        final var context = tenantContext.current();
        final var tenantId = context.tenantId();

        return authorization.require(new AttendeeAuthorizationPort.AuthorizationRequest(
                        tenantId, context.actorId(), AttendeeAuthorizationPort.Action.CREATE_PROFILE))
                .flatMap(ignored -> unitOfWork.execute(() ->
                        parties.findParty(PartyId.of(command.partyId()))
                                .flatMap(party -> {
                                    if (party.isEmpty()) {
                                        return Uni.createFrom().item(Result.<CreateAttendeeProfileResult>failure(
                                                ApplicationError.of("ATTENDEE_PARTY_NOT_FOUND",
                                                        "The referenced canonical Party does not exist")));
                                    }
                                    return profiles.findByTenantPartyAndKind(
                                                    tenantId, command.partyId(), command.profileKind())
                                            .flatMap(existing -> {
                                                if (existing.isPresent()) {
                                                    return Uni.createFrom().item(Result.<CreateAttendeeProfileResult>failure(
                                                            ApplicationError.of("ATTENDEE_PROFILE_ALREADY_EXISTS",
                                                                    "An attendee profile already exists for this Party and profile kind")));
                                                }
                                                var profile = AttendeeProfile.create(
                                                        UUID.randomUUID(), tenantId, PartyId.of(command.partyId()),
                                                        command.profileKind(), command.displayName(),
                                                        command.preferredName(), command.preferredLanguage(),
                                                        clock.instant());
                                                return profiles.save(profile).map(saved ->
                                                        Result.success(new CreateAttendeeProfileResult(
                                                                saved.id(), saved.status(), saved.revision())));
                                            });
                                })));
    }
}
