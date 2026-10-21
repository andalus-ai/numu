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
 * Semester entity (Indonesian schools typically have two semesters per year)
 */
@Entity
@Table(name = "semester")
public class Semester extends PanacheEntity {
    
    @NotBlank(message = "Nama semester wajib diisi")
    public String namaSemester; // e.g., "Semester 1", "Semester 2"
    
    @ManyToOne
    @NotNull(message = "Tahun ajaran wajib diisi")
    public TahunAjaran tahunAjaran;
    
    @NotNull(message = "Tanggal mulai wajib diisi")
    public LocalDate tanggalMulai;
    
    @NotNull(message = "Tanggal selesai wajib diisi")
    public LocalDate tanggalSelesai;
    
    @Enumerated(EnumType.STRING)
    public StatusSemester status = StatusSemester.PREPARATION;
    
    @CreationTimestamp
    public LocalDateTime createdAt;
    
    @UpdateTimestamp
    public LocalDateTime updatedAt;
    
    public enum StatusSemester {
        PREPARATION, // Setting up
        ACTIVE, // Currently ongoing
        COMPLETED, // Finished
        ARCHIVED // Archived
    }
    
    // Business methods
    public static Semester findActive() {
        return find("status", StatusSemester.ACTIVE).firstResult();
    }
    
    public static List<Semester> findByTahunAjaran(TahunAjaran tahunAjaran) {
        return list("tahunAjaran", tahunAjaran);
    }
}
