package tech.kayys.school.repository;

import java.time.LocalDate;
import java.util.List;

import io.quarkus.hibernate.orm.panache.PanacheRepository;
import jakarta.enterprise.context.ApplicationScoped;
import tech.kayys.school.model.Absensi;
import tech.kayys.school.model.Kelas;
import tech.kayys.school.model.Semester;
import tech.kayys.school.model.Student;
import tech.kayys.school.model.TahunAjaran;

@ApplicationScoped
public class AbsensiRepository implements PanacheRepository<Absensi> {
    
    public List<Absensi> findByClassAndDate(Kelas kelas, LocalDate date) {
        return list("kelas = ?1 and tanggal = ?2", kelas, date);
    }
    
    public long countAbsencesByStudent(Student student, Absensi.StatusKehadiran status, 
                                    TahunAjaran tahunAjaran, Semester semester) {
        return count("siswa = ?1 and status = ?2 and tahunAjaran = ?3 and semester = ?4", 
                    student, status, tahunAjaran, semester);
    }
    
    public double calculateAttendancePercentage(Student student, 
                                             TahunAjaran tahunAjaran, Semester semester) {
        long totalDays = count("siswa = ?1 and tahunAjaran = ?2 and semester = ?3", 
                              student, tahunAjaran, semester);
        
        long daysPresent = count("siswa = ?1 and tahunAjaran = ?2 and semester = ?3 and status = ?4", 
                                student, tahunAjaran, semester, Absensi.StatusKehadiran.HADIR);
        
        return totalDays > 0 ? (double) daysPresent / totalDays * 100 : 0;
    }
}
