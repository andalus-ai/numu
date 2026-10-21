package tech.kayys.school.model;

import java.util.List;
import io.quarkus.hibernate.orm.panache.PanacheEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * Grade level entity - represents education levels in Indonesia
 * (SD, SMP, SMA/SMK with their respective grades)
 */
@Entity
@Table(name = "tingkat")
public class Tingkat extends PanacheEntity {
    
    @NotBlank(message = "Kode tingkat wajib diisi")
    @Column(unique = true)
    public String kodeTingkat; // e.g. "SD-1", "SMP-2", "SMA-3"
    
    @NotBlank(message = "Nama tingkat wajib diisi")
    public String namaTingkat; // e.g. "Kelas 1 SD", "Kelas 2 SMP", "Kelas 3 SMA"
    
    @Enumerated(EnumType.STRING)
    @NotNull(message = "Jenjang wajib diisi")
    public JenjangPendidikan jenjang; // Education level
    
    public enum JenjangPendidikan {
        SD, // Elementary (grades 1-6)
        SMP, // Junior High (grades 7-9)
        SMA, // Senior High (general; grades 10-12)
        SMK // Vocational High School (grades 10-12)
    }
    
    // Business methods
    public static List<Tingkat> findByJenjang(JenjangPendidikan jenjang) {
        return list("jenjang", jenjang);
    }
}