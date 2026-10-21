package tech.kayys.school.service;

import java.time.LocalDate;
import java.util.List;
import java.util.stream.Collectors;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import tech.kayys.school.model.Absensi;
import tech.kayys.school.model.Kelas;
import tech.kayys.school.model.MataPelajaran;
import tech.kayys.school.model.Semester;
import tech.kayys.school.model.Student;
import tech.kayys.school.model.TahunAjaran;
import tech.kayys.school.repository.AbsensiRepository;

@ApplicationScoped
public class AttendanceService {
    
    @Inject
    AbsensiRepository absensiRepository;
    
    @Transactional
    public void recordAttendance(Absensi absensi) {
        absensiRepository.persist(absensi);
    }
    
    @Transactional
    public void recordClassAttendance(Kelas kelas, LocalDate date, MataPelajaran mataPelajaran, 
                                   List<Absensi> attendanceRecords) {
        for (Absensi record : attendanceRecords) {
            record.kelas = kelas;
            record.tanggal = date;
            record.mataPelajaran = mataPelajaran;
            absensiRepository.persist(record);
        }
    }
    
    public List<Student> getStudentsWithPoorAttendance(Kelas kelas, TahunAjaran tahunAjaran, 
                                                   Semester semester, double threshold) {
        List<Student> students = Student.findByKelas(kelas);
        
        return students.stream()
                .filter(student -> {
                    double attendanceRate = absensiRepository.calculateAttendancePercentage(
                        student, tahunAjaran, semester);
                    return attendanceRate < threshold;
                })
                .collect(Collectors.toList());
    }
    
    public List<Absensi> getAbsencesByDateRange(Student student, LocalDate startDate, LocalDate endDate) {
        return Absensi.findBySiswaAndDateRange(student, startDate, endDate);
    }
}
