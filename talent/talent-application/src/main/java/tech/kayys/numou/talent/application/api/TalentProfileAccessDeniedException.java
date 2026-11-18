package tech.kayys.numou.talent.application.api;

public final class TalentProfileAccessDeniedException extends SecurityException {
    public TalentProfileAccessDeniedException(String action) {
        super("Not authorized to " + action + " this talent profile");
    }
}
