package tech.kayys.school.model;


import io.quarkus.hibernate.orm.panache.PanacheEntity;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Past;
import jakarta.validation.constraints.Pattern;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import javax.security.auth.Subject;

import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;


/**
 * Teacher entity - represents a teacher in the Indonesian education system
 * Contains specific fields like NIP (Nomor Induk Pegawai)
 */
@Entity
@Table(name = "guru")
public class Teacher extends PanacheEntity {
    
    @NotBlank(message = "NIP wajib diisi")
    @Column(unique = true)
    @NotBlank(message = "NIP harus diisi")
    @Pattern(regexp = "\\d{18}", message = "NIP harus terdiri dari 18 digit angka")
    public String nip; // Nomor Induk Pegawai
    
    @NotBlank(message = "Nama lengkap wajib diisi")
    public String namaLengkap;
    
    @Enumerated(EnumType.STRING)
    @NotNull(message = "Jenis kelamin wajib diisi")
    public JenisKelamin jenisKelamin;
    
    @NotNull(message = "Tanggal lahir wajib diisi")
    @Past(message = "Tanggal lahir harus di masa lalu")
    public LocalDate tanggalLahir;
    
    @NotBlank(message = "Tempat lahir wajib diisi")
    public String tempatLahir;
    
    @NotBlank(message = "Pendidikan terakhir wajib diisi")
    public String pendidikanTerakhir;
    
    @NotBlank(message = "Alamat wajib diisi")
    public String alamat;
    
    public String email;
    public String noTelepon;
    
    @ManyToMany
    public Set<MataPelajaran> mataPelajaranDiajar; // Subjects taught
    
    @OneToOne
    public Kelas kelasWali; // Homeroom teacher assignment
    
    @Enumerated(EnumType.STRING)
    @NotNull(message = "Status wajib diisi")
    public StatusGuru status = StatusGuru.AKTIF;
    
    @CreationTimestamp
    public LocalDateTime createdAt;
    
    @UpdateTimestamp
    public LocalDateTime updatedAt;
    
    public enum JenisKelamin {
        LAKI_LAKI, PEREMPUAN
    }
    
    public enum StatusGuru {
        AKTIF, TIDAK_AKTIF, CUTI, PENSIUN
    }
    
    // Business methods
    public static Teacher findByNip(String nip) {
        return find("nip", nip).firstResult();
    }
    
    public static List<Teacher> findByMataPelajaran(MataPelajaran mataPelajaran) {
        return list("mataPelajaranDiajar", mataPelajaran);
    }
    
    public static List<Teacher> findActive() {
        return list("status", StatusGuru.AKTIF);
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
    public Student.Gender gender;
    
    @NotBlank(message = "Alamat harus diisi")
    public String address;
    
    @NotBlank(message = "Nomor telepon harus diisi")
    public String phoneNumber;
    

    
    @NotNull(message = "Tanggal mulai mengajar harus diisi")
    public LocalDate startDate;
    
    @NotNull(message = "Pendidikan terakhir harus diisi")
    @Enumerated(EnumType.STRING)
    public Education education;
    
    @NotBlank(message = "Bidang studi harus diisi")
    public String fieldOfStudy;
    
    @NotNull(message = "Status kepegawaian harus diisi")
    @Enumerated(EnumType.STRING)
    public EmploymentStatus employmentStatus;
    
    @OneToMany(mappedBy = "teacher")
    public List<Subject> subjects = new ArrayList<>();
    
    @OneToOne(mappedBy = "homeTeacher")
    public Kelas classroom;
    
    @ManyToMany(mappedBy = "supervisors")
    public Set<Extracurricular> supervisedExtracurriculars = new HashSet<>();
    
    public enum Education {
        D3, S1, S2, S3
    }
    
    public enum EmploymentStatus {
        PNS, HONORER, KONTRAK, TETAP_YAYASAN
    }
}
