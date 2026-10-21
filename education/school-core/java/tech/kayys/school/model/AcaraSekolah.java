package tech.kayys.school.model;


import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import io.quarkus.hibernate.orm.panache.PanacheEntity;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * School events entity
 */
@Entity
@Table(name = "acara_sekolah")
public class AcaraSekolah extends PanacheEntity {
    
    @NotBlank(message = "Nama acara wajib diisi")
    public String namaAcara;
    
    public String deskripsi;
    
    @NotNull(message = "Tanggal mulai wajib diisi")
    public LocalDateTime tanggalMulai;
    
    @NotNull(message = "Tanggal selesai wajib diisi")
    public LocalDateTime tanggalSelesai;
    
    public String lokasi;
    
    @Enumerated(EnumType.STRING)
    @NotNull(message = "Jenis acara wajib diisi")
    public JenisAcara jenisAcara;
    
    @ManyToOne
    public Kelas kelas; // Optional - can be null for school-wide events
    
    @ManyToOne
    @NotNull(message = "PIC wajib diisi")
    public Teacher penanggungjawab; // Person in charge
    
    @ManyToOne
    @NotNull(message = "Tahun ajaran wajib diisi")
    public TahunAjaran tahunAjaran;
    
    @ManyToOne
    public Semester semester; // Optional - not all events are tied to a semester
    
    @Enumerated(EnumType.STRING)
    @NotNull(message = "Status acara wajib diisi")
    public StatusAcara statusAcara = StatusAcara.DIRENCANAKAN;
    
    @CreationTimestamp
    public LocalDateTime createdAt;
    
    @UpdateTimestamp
    public LocalDateTime updatedAt;
    
    public enum JenisAcara {
        AKADEMIK, // Academic events
        OLAHRAGA, // Sports events
        SENI, // Arts events
        KEAGAMAAN, // Religious events
        KEMASYARAKATAN, // Community events
        RAPAT // Meetings
    }
    
    public enum StatusAcara {
        DIRENCANAKAN, // Planned
        BERLANGSUNG, // Ongoing
        SELESAI, // Completed
        DIBATALKAN // Cancelled
    }
    
    // Business methods
    public static List<AcaraSekolah> findByJenisAcara(JenisAcara jenisAcara) {
        return list("jenisAcara", jenisAcara);
    }
    
    public static List<AcaraSekolah> findByTanggal(LocalDate tanggal) {
        LocalDateTime startOfDay = tanggal.atStartOfDay();
        LocalDateTime endOfDay = tanggal.plusDays(1).atStartOfDay().minusSeconds(1);
        
        return list("tanggalMulai <= ?2 and tanggalSelesai >= ?1", 
                   startOfDay, endOfDay);
    }
    
    public static List<AcaraSekolah> findUpcomingEvents() {
        return list("tanggalMulai >= ?1 and statusAcara = ?2", 
                   LocalDateTime.now(), StatusAcara.DIRENCANAKAN);
    }
}
