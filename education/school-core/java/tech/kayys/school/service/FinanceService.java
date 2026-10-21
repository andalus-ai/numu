package tech.kayys.school.service;

import java.time.LocalDate;
import java.util.List;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import tech.kayys.school.model.Pembayaran;
import tech.kayys.school.model.Student;
import tech.kayys.school.model.TahunAjaran;
import tech.kayys.school.repository.PembayaranRepository;

@ApplicationScoped
public class FinanceService {
    
    @Inject
    PembayaranRepository pembayaranRepository;
    
    @Transactional
    public Pembayaran createInvoice(Pembayaran pembayaran) {
        pembayaranRepository.persist(pembayaran);
        return pembayaran;
    }
    
    @Transactional
    public void recordPayment(Long invoiceId, LocalDate paymentDate, String method, String proofOfPayment) {
        Pembayaran invoice = pembayaranRepository.findById(invoiceId);
        if (invoice == null) {
            throw new IllegalArgumentException("Invoice not found with ID: " + invoiceId);
        }
        
        invoice.processPayment(paymentDate, method, proofOfPayment);
    }
    
    @Transactional
    public void cancelInvoice(Long invoiceId) {
        Pembayaran invoice = pembayaranRepository.findById(invoiceId);
        if (invoice == null) {
            throw new IllegalArgumentException("Invoice not found with ID: " + invoiceId);
        }
        
        invoice.statusPembayaran = Pembayaran.StatusPembayaran.DIBATALKAN;
    }
    
    public List<Pembayaran> getOutstandingInvoices(Student student) {
        return pembayaranRepository.findOutstandingByStudent(student);
    }
    
    public double calculateTotalRevenue(TahunAjaran tahunAjaran) {
        List<Pembayaran> payments = Pembayaran.list(
            "tahunAjaran = ?1 and statusPembayaran = ?2", 
            tahunAjaran, Pembayaran.StatusPembayaran.LUNAS);
        
        return payments.stream()
                .mapToDouble(p -> p.jumlah)
                .sum();
    }
    
    public double calculateOutstandingPayments(TahunAjaran tahunAjaran) {
        List<Pembayaran> payments = Pembayaran.list(
            "tahunAjaran = ?1 and statusPembayaran = ?2", 
            tahunAjaran, Pembayaran.StatusPembayaran.BELUM_LUNAS);
        
        return payments.stream()
                .mapToDouble(p -> p.jumlah)
                .sum();
    }
}
