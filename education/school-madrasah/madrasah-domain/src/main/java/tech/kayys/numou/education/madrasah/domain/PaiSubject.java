package tech.kayys.numou.education.madrasah.domain;

/** The Pendidikan Agama Islam and Arabic subject group taught in madrasah. */
public enum PaiSubject {
    QURAN_HADITS("Al-Qur'an Hadis"),
    AKIDAH_AKHLAK("Akidah Akhlak"),
    FIKIH("Fikih"),
    SKI("Sejarah Kebudayaan Islam"),
    BAHASA_ARAB("Bahasa Arab");

    private final String label;

    PaiSubject(String label) { this.label = label; }

    public String label() { return label; }
}
