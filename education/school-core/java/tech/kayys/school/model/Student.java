package tech.kayys.school.model;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;

import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import io.quarkus.hibernate.orm.panache.PanacheEntity;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Past;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Student entity - represents a student in the Indonesian education system
 * Contains specific fields like NISN (Nomor Induk Siswa Nasional)
 */
@Entity
@Table(name = "student")
public class Student extends PanacheEntity {
    
    @NotBlank(message = "NISN wajib diisi")
    @Size(min = 10, max = 10, message = "NISN harus 10 digit")
    @Pattern(regexp = "\\d{10}", message = "NISN harus terdiri dari 10 digit angka")
    @Column(unique = true)
    public String nisn; // Nomor Induk Siswa Nasional - 10 digit unique identifier
    @NotBlank(message = "NIS harus diisi")
    @Column(unique = true)
    public String nis; // Nomor Induk Siswa (school-specific ID)

    
    @NotBlank(message = "Nama lengkap wajib diisi")
    public String namaLengkap;
    
    @NotBlank(message = "Nama panggilan wajib diisi")
    public String namaPanggilan;
    
    @Enumerated(EnumType.STRING)
    @NotNull(message = "Jenis kelamin wajib diisi")
    public JenisKelamin jenisKelamin;
    
    @NotNull(message = "Tanggal lahir wajib diisi")
    @Past(message = "Tanggal lahir harus di masa lalu")
    public LocalDate tanggalLahir;
    
    @NotBlank(message = "Tempat lahir wajib diisi")
    public String tempatLahir;
    
    @NotBlank(message = "Agama wajib diisi")
    public String agama; // Religion
    
    @NotBlank(message = "Alamat wajib diisi")
    public String alamat;
    
    public String email;
    public String noTelepon;
   
    @NotBlank(message = "Nama orang tua/wali wajib diisi")
    public String namaOrangTua;
    
    public String noTeleponOrangTua;
    
    @ManyToOne
    public Kelas kelas; // Class/Homeroom
    
    @OneToMany(mappedBy = "siswa", cascade = CascadeType.ALL)
    public List<Assessment> daftarNilai; // Grades
    
    @OneToMany(mappedBy = "siswa", cascade = CascadeType.ALL)
    public List<Absensi> daftarAbsensi; // Attendance records
    
    @Enumerated(EnumType.STRING)
    @NotNull(message = "Status wajib diisi")
    public StatusSiswa status = StatusSiswa.AKTIF;
    
    @CreationTimestamp
    public LocalDateTime createdAt;
    
    @UpdateTimestamp
    public LocalDateTime updatedAt;
    
    public enum JenisKelamin {
        LAKI_LAKI, PEREMPUAN
    }
    
    public enum StatusSiswa {
        AKTIF, TIDAK_AKTIF, PINDAH, LULUS, DIKELUARKAN
    }
    
    // Business methods
    public static Student findByNisn(String nisn) {
        return find("nisn", nisn).firstResult();
    }
    
    public static List<Student> findByKelas(Kelas kelas) {
        return list("kelas", kelas);
    }
    
    public static List<Student> findActive() {
        return list("status", StatusSiswa.AKTIF);
    }
    
    public double calculateAverage(TahunAjaran tahunAjaran, Semester semester) {
        return daftarNilai.stream()
                .filter(n -> n.tahunAjaran.equals(tahunAjaran) && n.semester.equals(semester))
                .mapToDouble(n -> n.nilaiAkhir)
                .average()
                .orElse(0);
    }


    @NotBlank(message = "Nama lengkap harus diisi")
    public String fullName;

    @NotNull(message = "Tanggal lahir harus diisi")
    @Past(message = "Tanggal lahir harus di masa lalu")
    public LocalDate dateOfBirth;

    @NotBlank(message = "Tempat lahir harus diisi")
    public String placeOfBirth;
    
    @Enumerated(EnumType.STRING)
    @NotNull(message = "Jenis kelamin harus diisi")
    public Gender gender;
    
    @NotBlank(message = "Alamat harus diisi")
    public String address;
    
    @NotBlank(message = "Nomor telepon harus diisi")
    public String phoneNumber;
    
    
    @NotNull(message = "Status aktif harus diisi")
    public Boolean active = true;
    
    @NotNull(message = "Tanggal pendaftaran harus diisi")
    public LocalDate registrationDate;
    
    @ManyToOne
    @JoinColumn(name = "classroom_id")
    public Kelas classroom;
    
    @ManyToOne
    @JoinColumn(name = "parent_id")
    public Parent parent;
    
    @ManyToMany(mappedBy = "students")
    public Set<Extracurricular> extracurriculars = new HashSet<>();
    
    public enum Gender {
        LAKI_LAKI, PEREMPUAN
    }
}
