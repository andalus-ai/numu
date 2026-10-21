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
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/**
 * Student grades entity
 */
@Entity
@Table(name = "assesment")
public class Assessment extends PanacheEntity {
    
    @ManyToOne
    @NotNull(message = "Siswa wajib diisi")
    public Student siswa;
    
    @ManyToOne
    @NotNull(message = "Mata pelajaran wajib diisi")
    public MataPelajaran mataPelajaran;
    
    @ManyToOne
    @NotNull(message = "Guru wajib diisi")
    public Teacher guru;
    
    @ManyToOne
    @NotNull(message = "Tahun ajaran wajib diisi")
    public TahunAjaran tahunAjaran;
    
    @ManyToOne
    @NotNull(message = "Semester wajib diisi")
    public Semester semester;
    
    @Enumerated(EnumType.STRING)
    @NotNull(message = "Jenis penilaian wajib diisi")
    public JenisPenilaian jenisPenilaian;
    
    public String deskripsi;
    
    public double nilaiPengetahuan; // Knowledge score
    
    public double nilaiKeterampilan; // Skills score
    
    public double nilaiSikap; // Attitude score
    
    public double nilaiAkhir; // Final score (calculated)
    
    @CreationTimestamp
    public LocalDateTime createdAt;
    
    @UpdateTimestamp
    public LocalDateTime updatedAt;
    
    public enum JenisPenilaian {
        TUGAS, // Assignment
        ULANGAN_HARIAN, // Daily test
        UJIAN_TENGAH_SEMESTER, // Mid-term exam
        UJIAN_AKHIR_SEMESTER, // Final exam
        PRAKTIKUM, // Practical work
        PROYEK // Project
    }
    
    // Business methods
    public static List<Assessment> findBySiswa(Student siswa) {
        return list("siswa", siswa);
    }
    
    public static List<Assessment> findByMataPelajaran(MataPelajaran mataPelajaran) {
        return list("mataPelajaran", mataPelajaran);
    }
    
    public static List<Assessment> findBySiswaAndSemester(Student siswa, Semester semester) {
        return list("siswa = ?1 and semester = ?2", siswa, semester);
    }
    
    // Calculate final grade based on weighted components
    public void calculateFinalGrade() {
        double weightPengetahuan = 0.4; // 40% knowledge
        double weightKeterampilan = 0.4; // 40% skills
        double weightSikap = 0.2; // 20% attitude
        
        this.nilaiAkhir = (nilaiPengetahuan * weightPengetahuan) +
                         (nilaiKeterampilan * weightKeterampilan) +
                         (nilaiSikap * weightSikap);
    }
    
    public boolean isPassed() {
        return nilaiAkhir >= mataPelajaran.kkm;
    }

     @ManyToOne
    @JoinColumn(name = "student_id")
    @NotNull(message = "Siswa harus diisi")
    public Student student;
    
    @ManyToOne
    @JoinColumn(name = "subject_id")
    @NotNull(message = "Mata pelajaran harus diisi")
    public Subject subject;
    
    @NotNull(message = "Jenis penilaian harus diisi")
    @Enumerated(EnumType.STRING)
    public AssessmentType type;
    
    @NotBlank(message = "Judul penilaian harus diisi")
    public String title;
    
    @NotNull(message = "Tanggal penilaian harus diisi")
    public LocalDate date;
    
    @Min(value = 0, message = "Nilai minimal 0")
    @Max(value = 100, message = "Nilai maksimal 100")
    @NotNull(message = "Nilai harus diisi")
    public Double score;
    
    @NotNull(message = "Semester harus diisi")
    @Enumerated(EnumType.STRING)
    public Semester semester;
    
    public String notes;
    
    public enum AssessmentType {
        TUGAS, ULANGAN_HARIAN, UTS, UAS, PRAKTIKUM, PROYEK
    }
    
    public enum Semester {
        GANJIL, GENAP
    }
}