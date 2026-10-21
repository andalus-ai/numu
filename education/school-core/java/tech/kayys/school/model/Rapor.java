package tech.kayys.school.model;


import java.time.LocalDateTime;
import java.util.List;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import io.quarkus.hibernate.orm.panache.PanacheEntity;
import jakarta.persistence.Entity;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotNull;

/**
 * Report card entity
 */
@Entity
@Table(name = "rapor")
public class Rapor extends PanacheEntity {
    
    @ManyToOne
    @NotNull(message = "Siswa wajib diisi")
    public Student siswa;
    
    @ManyToOne
    @NotNull(message = "Kelas wajib diisi")
    public Kelas kelas;
    
    @ManyToOne
    @NotNull(message = "Tahun ajaran wajib diisi")
    public TahunAjaran tahunAjaran;
    
    @ManyToOne
    @NotNull(message = "Semester wajib diisi")
    public Semester semester;
    
    public double nilaiRataRata; // Average grade
    
    public int peringkat; // Class rank
    
    public String catatanWaliKelas; // Homeroom teacher notes
    
    public int jumlahHadir; // Days present
    public int jumlahSakit; // Days absent due to illness
    public int jumlahIzin; // Days absent with permission
    public int jumlahAlpa; // Days absent without permission
    
    @CreationTimestamp
    public LocalDateTime createdAt;
    
    @UpdateTimestamp
    public LocalDateTime updatedAt;
    
    // Business methods
    public static List<Rapor> findBySiswa(Student siswa) {
        return list("siswa", siswa);
    }
    
    public static List<Rapor> findByKelas(Kelas kelas) {
        return list("kelas", kelas);
    }
    
    public static List<Rapor> findBySemester(Semester semester) {
        return list("semester", semester);
    }
    
    public void calculateAverageGrade() {
        List<Assessment> nilaiList = Assessment.list("siswa = ?1 and tahunAjaran = ?2 and semester = ?3", 
                                          siswa, tahunAjaran, semester);
        
        this.nilaiRataRata = nilaiList.stream()
                .mapToDouble(n -> n.nilaiAkhir)
                .average()
                .orElse(0);
    }
    
    public void calculateAttendance() {
        List<Absensi> absensiList = Absensi.list("siswa = ?1 and tahunAjaran = ?2 and semester = ?3", 
                                               siswa, tahunAjaran, semester);
        
        this.jumlahHadir = (int) absensiList.stream()
                .filter(a -> a.status == Absensi.StatusKehadiran.HADIR)
                .count();
        
        this.jumlahSakit = (int) absensiList.stream()
                .filter(a -> a.status == Absensi.StatusKehadiran.SAKIT)
                .count();
        
        this.jumlahIzin = (int) absensiList.stream()
                .filter(a -> a.status == Absensi.StatusKehadiran.IZIN)
                .count();
        
        this.jumlahAlpa = (int) absensiList.stream()
                .filter(a -> a.status == Absensi.StatusKehadiran.ALPA)
                .count();
    }
}
