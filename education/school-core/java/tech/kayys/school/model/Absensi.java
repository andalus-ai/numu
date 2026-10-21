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
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotNull;
import tech.kayys.school.model.Absensi.StatusKehadiran;

/**
 * Attendance entity
 */
@Entity
@Table(name = "absensi")
public class Absensi extends PanacheEntity {

        @ManyToOne
    @JoinColumn(name = "student_id")
    @NotNull(message = "Siswa harus diisi")
    public Student student;
    
    @NotNull(message = "Tanggal harus diisi")
    public LocalDate date;
    
    @NotNull(message = "Status kehadiran harus diisi")
    @Enumerated(EnumType.STRING)
    public AttendanceStatus status;
    
    public String notes;
    
    public enum AttendanceStatus {
        HADIR, SAKIT, IZIN, ALPA
    }
    
    @ManyToOne
    @NotNull(message = "Siswa wajib diisi")
    public Student siswa;
    
    @ManyToOne
    @NotNull(message = "Kelas wajib diisi")
    public Kelas kelas;
    
    @NotNull(message = "Tanggal wajib diisi")
    public LocalDate tanggal;
    
    @ManyToOne
    public MataPelajaran mataPelajaran; // Optional - for subject-specific attendance
 
    
    public String keterangan; // Notes/reason for absence
    
    @ManyToOne
    @NotNull(message = "Tahun ajaran wajib diisi")
    public TahunAjaran tahunAjaran;
    
    @ManyToOne
    @NotNull(message = "Semester wajib diisi")
    public Semester semester;
    
    @CreationTimestamp
    public LocalDateTime createdAt;
    
    @UpdateTimestamp
    public LocalDateTime updatedAt;
    
    public enum StatusKehadiran {
        HADIR, // Present
        SAKIT, // Sick
        IZIN, // Absent with permission
        ALPA // Absent without permission
    }
    
    // Business methods
    public static List<Absensi> findBySiswa(Student siswa) {
        return list("siswa", siswa);
    }
    
    public static List<Absensi> findByKelas(Kelas kelas) {
        return list("kelas", kelas);
    }
    
    public static List<Absensi> findByTanggal(LocalDate tanggal) {
        return list("tanggal", tanggal);
    }
    
    public static List<Absensi> findBySiswaAndDateRange(Student siswa, LocalDate startDate, LocalDate endDate) {
        return list("siswa = ?1 and tanggal >= ?2 and tanggal <= ?3", 
                   siswa, startDate, endDate);
    }
}
