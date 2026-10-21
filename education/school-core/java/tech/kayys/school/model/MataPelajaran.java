package tech.kayys.school.model;


import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import io.quarkus.hibernate.orm.panache.PanacheEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * Subject entity - represents subjects in Indonesian curriculum
 */
@Entity
@Table(name = "mata_pelajaran")
public class MataPelajaran extends PanacheEntity {
    
    @NotBlank(message = "Kode mata pelajaran wajib diisi")
    @Column(unique = true)
    public String kodeMataPelajaran;
    
    @NotBlank(message = "Nama mata pelajaran wajib diisi")
    public String namaMataPelajaran;
    
    @ManyToOne
    @NotNull(message = "Kurikulum wajib diisi")
    public Kurikulum kurikulum; // Linked to curriculum (e.g., K13, Merdeka)
    
    public int kkm; // Minimum passing grade (Kriteria Ketuntasan Minimal)
    
    public int jumlahJam; // Hours per week
    
    @ManyToMany(mappedBy = "mataPelajaranDiajar")
    public Set<Teacher> guruPengajar; // Teachers who teach this subject
    
    @OneToMany(mappedBy = "mataPelajaran")
    public List<JadwalPelajaran> jadwalPelajaran; // When this subject is taught
    
    @CreationTimestamp
    public LocalDateTime createdAt;
    
    @UpdateTimestamp
    public LocalDateTime updatedAt;
    
    // Business methods
    public static List<MataPelajaran> findByKurikulum(Kurikulum kurikulum) {
        return list("kurikulum", kurikulum);
    }
}
