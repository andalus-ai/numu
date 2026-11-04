package tech.kayys.numou.talent.application.api;

import tech.kayys.numou.talent.domain.profile.TalentProfileId;

public final class TalentProfileNotFoundException extends RuntimeException {
    public TalentProfileNotFoundException(TalentProfileId id) {
        super("Talent profile not found: " + id);
    }
}
