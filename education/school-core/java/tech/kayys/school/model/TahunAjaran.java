package tech.kayys.school.model;

import java.time.LocalDate;
import java.time.LocalDateTime;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import io.quarkus.hibernate.orm.panache.PanacheEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * Academic year entity
 */
@Entity
@Table(name = "tahun_ajaran")
public class TahunAjaran extends PanacheEntity {
    
    @NotBlank(message = "Nama tahun ajaran wajib diisi")
    @Column(unique = true)
    public String namaTahunAjaran; // e.g., "2024/2025"
    
    @NotNull(message = "Tanggal mulai wajib diisi")
    public LocalDate tanggalMulai;
    
    @NotNull(message = "Tanggal selesai wajib diisi")
    public LocalDate tanggalSelesai;
    
    @Enumerated(EnumType.STRING)
    public StatusTahunAjaran status = StatusTahunAjaran.PREPARATION;
    
    @CreationTimestamp
    public LocalDateTime createdAt;
    
    @UpdateTimestamp
    public LocalDateTime updatedAt;
    
    public enum StatusTahunAjaran {
        PREPARATION, // Setting up
        ACTIVE, // Currently ongoing
        COMPLETED, // Finished
        ARCHIVED // Archived
    }
    
    // Business methods
    public static TahunAjaran findActive() {
        return find("status", StatusTahunAjaran.ACTIVE).firstResult();
    }
    
    public boolean isActive() {
        LocalDate today = LocalDate.now();
        return today.isAfter(tanggalMulai) && today.isBefore(tanggalSelesai) && 
               status == StatusTahunAjaran.ACTIVE;
    }
}
