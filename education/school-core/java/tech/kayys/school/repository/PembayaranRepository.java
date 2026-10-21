package tech.kayys.school.repository;

import java.util.List;

import io.quarkus.hibernate.orm.panache.PanacheRepository;
import jakarta.enterprise.context.ApplicationScoped;
import tech.kayys.school.model.Pembayaran;
import tech.kayys.school.model.Student;
import tech.kayys.school.model.TahunAjaran;

@ApplicationScoped
public class PembayaranRepository implements PanacheRepository<Pembayaran> {
    
    public List<Pembayaran> findOutstandingByStudent(Student student) {
        return list("siswa = ?1 and statusPembayaran = ?2", 
                   student, Pembayaran.StatusPembayaran.BELUM_LUNAS);
    }
    
    public double calculateTotalPaidByStudent(Student student, TahunAjaran tahunAjaran) {
        List<Pembayaran> payments = list("siswa = ?1 and tahunAjaran = ?2 and statusPembayaran = ?3", 
                                       student, tahunAjaran, Pembayaran.StatusPembayaran.LUNAS);
        
        return payments.stream()
                .mapToDouble(p -> p.jumlah)
                .sum();
    }
    
    public double calculateTotalOutstandingByStudent(Student student, TahunAjaran tahunAjaran) {
        List<Pembayaran> payments = list("siswa = ?1 and tahunAjaran = ?2 and statusPembayaran = ?3", 
                                       student, tahunAjaran, Pembayaran.StatusPembayaran.BELUM_LUNAS);
        
        return payments.stream()
                .mapToDouble(p -> p.jumlah)
                .sum();
    }
}