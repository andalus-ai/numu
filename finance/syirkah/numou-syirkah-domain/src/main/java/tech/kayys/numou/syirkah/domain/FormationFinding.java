package tech.kayys.numou.numoudomain;

/**
 * One reason an akad cannot yet be treated as validly formed.
 *
 * @param code    stable code
 * @param defect  how the book classifies it
 * @param message what is wrong
 * @param source  where in Hukum Muamalah the rule comes from, for example "Bab X, syarat in'iqad 1"
 */
public record FormationFinding(String code, AkadDefect defect, String message, String source) {
    @Override
    public String toString() {
        return code + " [" + defect + "]: " + message + " (" + source + ")";
    }
}
