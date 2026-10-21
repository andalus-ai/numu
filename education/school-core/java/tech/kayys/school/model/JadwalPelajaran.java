package tech.kayys.school.model;


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
import jakarta.validation.constraints.NotNull;

/**
 * Class schedule entity
 */
@Entity
@Table(name = "jadwal_pelajaran")
public class JadwalPelajaran extends PanacheEntity {
    
    @ManyToOne
    @NotNull(message = "Kelas wajib diisi")
    public Kelas kelas;
    
    @ManyToOne
    @NotNull(message = "Mata pelajaran wajib diisi")
    public MataPelajaran mataPelajaran;
    
    @ManyToOne
    @NotNull(message = "Guru wajib diisi")
    public Teacher guru;
    
    @Enumerated(EnumType.STRING)
    @NotNull(message = "Hari wajib diisi")
    public Hari hari;
    
    @NotNull(message = "Jam mulai wajib diisi")
    public String jamMulai; // e.g., "07:30"
    
    @NotNull(message = "Jam selesai wajib diisi")
    public String jamSelesai; // e.g., "09:00"
    
    @ManyToOne
    @NotNull(message = "Semester wajib diisi")
    public Semester semester;
    
    @CreationTimestamp
    public LocalDateTime createdAt;
    
    @UpdateTimestamp
    public LocalDateTime updatedAt;
    
    public enum Hari {
        SENIN, SELASA, RABU, KAMIS, JUMAT, SABTU
    }
    
    // Business methods
    public static List<JadwalPelajaran> findByKelas(Kelas kelas) {
        return list("kelas", kelas);
    }
    
    public static List<JadwalPelajaran> findByGuru(Teacher guru) {
        return list("guru", guru);
    }
    
    public static List<JadwalPelajaran> findByHari(Hari hari) {
        return list("hari", hari);
    }
}
