package tech.kayys.school.repository;

import java.util.List;

import io.quarkus.hibernate.orm.panache.PanacheRepository;
import jakarta.enterprise.context.ApplicationScoped;
import tech.kayys.school.model.Kelas;
import tech.kayys.school.model.MataPelajaran;
import tech.kayys.school.model.Assessment;
import tech.kayys.school.model.Semester;
import tech.kayys.school.model.Student;
import tech.kayys.school.model.TahunAjaran;

@ApplicationScoped
public class NilaiRepository implements PanacheRepository<Assessment> {
    
    public List<Assessment> findByStudentAndSubject(Student student, MataPelajaran mataPelajaran, 
                                             TahunAjaran tahunAjaran, Semester semester) {
        return list("siswa = ?1 and mataPelajaran = ?2 and tahunAjaran = ?3 and semester = ?4", 
                   student, mataPelajaran, tahunAjaran, semester);
    }
    
    public List<Assessment> findFailingGrades(MataPelajaran mataPelajaran) {
        return find("mataPelajaran = ?1 and nilaiAkhir < ?2", 
                   mataPelajaran, mataPelajaran.kkm).list();
    }
    
    public double getClassAverage(Kelas kelas, MataPelajaran mataPelajaran, 
                                TahunAjaran tahunAjaran, Semester semester) {
        List<Assessment> grades = find("siswa.kelas = ?1 and mataPelajaran = ?2 and tahunAjaran = ?3 and semester = ?4", 
                                kelas, mataPelajaran, tahunAjaran, semester).list();
        
        return grades.stream()
                .mapToDouble(n -> n.nilaiAkhir)
                .average()
                .orElse(0);
    }
}
