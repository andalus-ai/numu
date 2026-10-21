package tech.kayys.school.repository;


import io.quarkus.hibernate.orm.panache.PanacheRepository;
import jakarta.enterprise.context.ApplicationScoped;
import tech.kayys.school.model.Kelas;
import tech.kayys.school.model.Student;

import java.time.LocalDate;
import java.util.List;

@ApplicationScoped
public class StudentRepository implements PanacheRepository<Student> {
    
    public List<Student> findByName(String name) {
        return list("namaLengkap like ?1 or namaPanggilan like ?1", "%" + name + "%");
    }
    
    public List<Student> findByClass(Kelas kelas) {
        return list("kelas", kelas);
    }
    
    public List<Student> findByStatus(Student.StatusSiswa status) {
        return list("status", status);
    }
    
    public List<Student> findByAgeRange(int minAge, int maxAge) {
        LocalDate minDate = LocalDate.now().minusYears(maxAge);
        LocalDate maxDate = LocalDate.now().minusYears(minAge);
        
        return list("tanggalLahir >= ?1 and tanggalLahir <= ?2", minDate, maxDate);
    }
}
