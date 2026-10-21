package tech.kayys.school.model;


import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import io.quarkus.hibernate.orm.panache.PanacheEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * Class/Homeroom entity - represents a class in Indonesian schools
 */

 @Entity
 @Table(name = "classroom")
 public class Kelas extends PanacheEntity { 
    
    @NotBlank(message = "Nama kelas wajib diisi")
    @Column(unique = true)
    public String namaKelas; // e.g., "X IPA 1", "XI IPS 2"
    
    @ManyToOne
    @NotNull(message = "Tingkat wajib diisi")
    public Tingkat tingkat; // Grade level (SD, SMP, SMA/SMK)
    
    @NotNull(message = "Tahun ajaran wajib diisi")
    @ManyToOne
    public TahunAjaran tahunAjaran;
    
    @OneToOne
    public Teacher waliKelas; // Homeroom teacher
    
    @OneToMany(mappedBy = "kelas")
    public List<Student> daftarSiswa; // Students in this class
    
    @OneToMany(mappedBy = "kelas")
    public List<JadwalPelajaran> jadwalPelajaran; // Class schedule
    
    @Enumerated(EnumType.STRING)
    public StatusKelas status = StatusKelas.AKTIF;
    
    @CreationTimestamp
    public LocalDateTime createdAt;
    
    @UpdateTimestamp
    public LocalDateTime updatedAt;
    
    public enum StatusKelas {
        AKTIF, TIDAK_AKTIF
    }
    
    // Business methods
    public static List<Kelas> findByTingkat(Tingkat tingkat) {
        return list("tingkat", tingkat);
    }
    
    public static List<Kelas> findByTahunAjaran(TahunAjaran tahunAjaran) {
        return list("tahunAjaran", tahunAjaran);
    }
    
    public static List<Kelas> findByWaliKelas(Teacher waliKelas) {
        return list("waliKelas", waliKelas);
    }
    
    public int getJumlahSiswa() {
        return daftarSiswa != null ? daftarSiswa.size() : 0;
    }

    @NotBlank(message = "Nama kelas harus diisi")
    public String name;
    
    @Min(value = 1, message = "Kapasitas minimal 1")
    @Max(value = 40, message = "Kapasitas maksimal 40")
    @NotNull(message = "Kapasitas harus diisi")
    public Integer capacity;
    
    @NotNull(message = "Tahun ajaran harus diisi")
    public String academicYear; // Format: "2024/2025"
    
    @ManyToOne
    @JoinColumn(name = "grade_id")
    @NotNull(message = "Tingkat harus diisi")
    public Grade grade;
    
    @OneToOne
    @JoinColumn(name = "teacher_id")
    public Teacher homeTeacher;
    
    @OneToMany(mappedBy = "classroom")
    public List<Student> students = new ArrayList<>();
    
    @OneToMany(mappedBy = "classroom")
    public List<TeachingSchedule> schedules = new ArrayList<>();
}

