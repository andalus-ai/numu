package tech.kayys.numou.feedback.domain;

/**
 * Bounded origin references only. Never place request bodies, credentials,
 * stack traces or copied business records in this value object.
 */
public record FeedbackContext(
        String application, String module, String screen,
        String recordType, String recordReference, String applicationVersion) {
    public FeedbackContext {
        application = normalize(application, "application", 100);
        module = normalize(module, "module", 100);
        screen = normalize(screen, "screen", 200);
        recordType = normalize(recordType, "recordType", 100);
        recordReference = normalize(recordReference, "recordReference", 200);
        applicationVersion = normalize(applicationVersion, "applicationVersion", 100);
    }
    public static FeedbackContext empty() {
        return new FeedbackContext(null, null, null, null, null, null);
    }
    private static String normalize(String value, String field, int max) {
        if (value == null || value.isBlank()) return null;
        String result = value.trim();
        if (result.length() > max) throw new IllegalArgumentException(field + " exceeds " + max + " characters");
        return result;
    }
}