package tech.kayys.school.model;



import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import io.quarkus.hibernate.orm.panache.PanacheEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * Payment/Finance entity
 */
@Entity
@Table(name = "pembayaran")
public class Pembayaran extends PanacheEntity {
    
    @ManyToOne
    public Student siswa; // Link to student if student-specific payment
    
    @NotBlank(message = "Nomor invoice wajib diisi")
    @Column(unique = true)
    public String nomorInvoice;
    
    @NotNull(message = "Tanggal wajib diisi")
    public LocalDate tanggalInvoice;
    
    @Enumerated(EnumType.STRING)
    @NotNull(message = "Jenis pembayaran wajib diisi")
    public JenisPembayaran jenisPembayaran;
    
    @NotBlank(message = "Deskripsi wajib diisi")
    public String deskripsi;
    
    @NotNull(message = "Jumlah wajib diisi")
    public double jumlah;
    
    @Enumerated(EnumType.STRING)
    @NotNull(message = "Status pembayaran wajib diisi")
    public StatusPembayaran statusPembayaran = StatusPembayaran.BELUM_LUNAS;
    
    public LocalDate tanggalPembayaran;
    
    public String metodePembayaran;
    
    public String buktiPembayaran;
    
    @ManyToOne
    @NotNull(message = "Tahun ajaran wajib diisi")
    public TahunAjaran tahunAjaran;
    
    @ManyToOne
    public Semester semester; // Optional - some payments aren't semester-specific
    
    @CreationTimestamp
    public LocalDateTime createdAt;
    
    @UpdateTimestamp
    public LocalDateTime updatedAt;
    
    public enum JenisPembayaran {
        SPP, // Monthly fee
        UANG_GEDUNG, // Building/facilities fee
        SERAGAM, // Uniform fee
        BUKU, // Book fee
        KEGIATAN, // Activities fee
        UJIAN, // Exam fee
        LAINNYA // Other payments
    }
    
    public enum StatusPembayaran {
        BELUM_LUNAS, // Unpaid
        LUNAS, // Paid
        DIBATALKAN // Canceled
    }
    
    // Business methods
    public static List<Pembayaran> findBySiswa(Student siswa) {
        return list("siswa", siswa);
    }
    
    public static List<Pembayaran> findByJenisPembayaran(JenisPembayaran jenisPembayaran) {
        return list("jenisPembayaran", jenisPembayaran);
    }
    
    public static List<Pembayaran> findByStatusPembayaran(StatusPembayaran statusPembayaran) {
        return list("statusPembayaran", statusPembayaran);
    }
    
    public static List<Pembayaran> findOutstandingPayments() {
        return list("statusPembayaran", StatusPembayaran.BELUM_LUNAS);
    }
    
    public static double calculateTotalPaymentsForPeriod(LocalDate startDate, LocalDate endDate) {
        List<Pembayaran> payments = list("tanggalPembayaran >= ?1 and tanggalPembayaran <= ?2 and statusPembayaran = ?3", 
                                       startDate, endDate, StatusPembayaran.LUNAS);
        
        return payments.stream()
                .mapToDouble(p -> p.jumlah)
                .sum();
    }
    
    public void processPayment(LocalDate paymentDate, String method, String proofOfPayment) {
        this.tanggalPembayaran = paymentDate;
        this.metodePembayaran = method;
        this.buktiPembayaran = proofOfPayment;
        this.statusPembayaran = StatusPembayaran.LUNAS;
    }
}

