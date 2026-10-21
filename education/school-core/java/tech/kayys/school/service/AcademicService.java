package tech.kayys.school.service;

import java.util.List;
import java.util.stream.Collectors;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import tech.kayys.school.model.Kelas;
import tech.kayys.school.model.MataPelajaran;
import tech.kayys.school.model.Assessment;
import tech.kayys.school.model.Rapor;
import tech.kayys.school.model.Semester;
import tech.kayys.school.model.Student;
import tech.kayys.school.model.TahunAjaran;
import tech.kayys.school.repository.KelasRepository;
import tech.kayys.school.repository.NilaiRepository;
import tech.kayys.school.repository.StudentRepository;

@ApplicationScoped
public class AcademicService {
    
    @Inject
    KelasRepository kelasRepository;
    
    @Inject
    NilaiRepository nilaiRepository;
    
    @Inject
    StudentRepository studentRepository;
    
    @Transactional
    public Kelas createClass(Kelas kelas) {
        kelasRepository.persist(kelas);
        return kelas;
    }
    
    @Transactional
    public void assignStudentToClass(Student student, Kelas kelas) {
        student.kelas = kelas;
    }
    
    @Transactional
    public void recordGrade(Assessment nilai) {
        // Calculate final grade based on components
        nilai.calculateFinalGrade();
        nilaiRepository.persist(nilai);
    }
    
    @Transactional
    public Rapor generateReportCard(Student student, Kelas kelas, TahunAjaran tahunAjaran, Semester semester) {
        Rapor rapor = new Rapor();
        rapor.siswa = student;
        rapor.kelas = kelas;
        rapor.tahunAjaran = tahunAjaran;
        rapor.semester = semester;
        
        // Calculate average grade
        rapor.calculateAverageGrade();
        
        // Calculate attendance
        rapor.calculateAttendance();
        
        // Calculate class rank
        List<Student> classmates = studentRepository.findByClass(kelas);
        List<Double> averages = classmates.stream()
                .map(s -> s.calculateAverage(tahunAjaran, semester))
                .sorted((a, b) -> Double.compare(b, a)) // Sort in descending order
                .collect(Collectors.toList());
        
        double studentAverage = student.calculateAverage(tahunAjaran, semester);
        rapor.peringkat = averages.indexOf(studentAverage) + 1;
        
        return rapor;
    }
    
    public List<Student> getTopStudents(Kelas kelas, TahunAjaran tahunAjaran, Semester semester, int limit) {
        List<Student> students = studentRepository.findByClass(kelas);
        
        return students.stream()
                .sorted((a, b) -> Double.compare(
                    b.calculateAverage(tahunAjaran, semester), 
                    a.calculateAverage(tahunAjaran, semester)))
                .limit(limit)
                .collect(Collectors.toList());
    }
    
    public List<MataPelajaran> getSubjectsWithHighestFailRate(Kelas kelas, TahunAjaran tahunAjaran, Semester semester) {
        // This would require more complex logic in a real application
        // For now, we'll just return a placeholder
        return List.of();
    }
}
