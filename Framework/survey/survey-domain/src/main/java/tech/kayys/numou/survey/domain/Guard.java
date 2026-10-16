package tech.kayys.numou.survey.domain;

public final class Guard {
    private Guard() {}

    public static String notBlank(String v, String name) {
        if (v == null || v.isBlank()) {
            throw new DomainException(ErrorCode.VALIDATION_FAILED, name + " must not be blank");
        }
        return v.trim();
    }

    public static <T> T notNull(T v, String name) {
        if (v == null) throw new DomainException(ErrorCode.VALIDATION_FAILED, name + " must not be null");
        return v;
    }
}
