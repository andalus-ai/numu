package tech.kayys.school.model;


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
 * Indonesian curriculum entity
 */
@Entity
@Table(name = "kurikulum")
public class Kurikulum extends PanacheEntity {
    
    @NotBlank(message = "Nama kurikulum wajib diisi")
    @Column(unique = true)
    public String namaKurikulum; // e.g., "K13", "Kurikulum Merdeka"
    
    @NotNull(message = "Tahun mulai wajib diisi")
    public int tahunMulai;
    
    public String deskripsi;
    
    @Enumerated(EnumType.STRING)
    public StatusKurikulum status = StatusKurikulum.AKTIF;
    
    @CreationTimestamp
    public LocalDateTime createdAt;
    
    @UpdateTimestamp
    public LocalDateTime updatedAt;
    
    public enum StatusKurikulum {
        AKTIF, TIDAK_AKTIF
    }
    
    // Business methods
    public static Kurikulum findActive() {
        return find("status", StatusKurikulum.AKTIF).firstResult();
    }
}
