package tech.kayys.numou.education.madrasah.application.command;

import tech.kayys.numou.education.madrasah.domain.MadrasahProfile;
import tech.kayys.numou.education.madrasah.domain.MadrasahProfileId;
import tech.kayys.numou.education.madrasah.domain.Nsm;
import tech.kayys.numou.education.madrasah.spi.MadrasahProfileRepository;
import tech.kayys.numou.foundation.application.result.Result;

import java.util.Objects;
import java.util.concurrent.CompletionStage;

/** Attaches a madrasah profile to an existing school; one profile per school. */
public class RegisterMadrasahProfileHandler {

    private final MadrasahProfileRepository profiles;

    public RegisterMadrasahProfileHandler(MadrasahProfileRepository profiles) {
        this.profiles = Objects.requireNonNull(profiles);
    }

    public CompletionStage<Result<MadrasahProfileId>> handle(RegisterMadrasahProfileCommand cmd) {
        return profiles.findBySchool(cmd.schoolId()).thenCompose(existing -> {
            if (existing.isPresent()) {
                return Outcomes.<MadrasahProfileId>failed("madrasah.profile.exists", "School already has a madrasah profile");
            }
            MadrasahProfile profile;
            try {
                profile = MadrasahProfile.register(MadrasahProfileId.generate(), cmd.tenantId(), cmd.schoolId(),
                        Nsm.of(cmd.nsm()), cmd.level(), cmd.ownership());
                if (cmd.tahfidzProgram()) profile.enableTahfidzProgram();
            } catch (IllegalArgumentException | NullPointerException e) {
                return Outcomes.<MadrasahProfileId>failed("madrasah.profile.invalid", e.getMessage());
            }
            return profiles.save(profile).thenApply(saved -> Result.success(saved.getId()));
        });
    }
}
