package tech.kayys.school.service;


import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import tech.kayys.school.model.Kelas;
import tech.kayys.school.model.Assessment;
import tech.kayys.school.model.Semester;
import tech.kayys.school.model.Student;
import tech.kayys.school.model.TahunAjaran;
import tech.kayys.school.repository.AbsensiRepository;
import tech.kayys.school.repository.NilaiRepository;
import tech.kayys.school.repository.PembayaranRepository;
import tech.kayys.school.repository.StudentRepository;

import java.util.List;

@ApplicationScoped
public class StudentService {
    
    @Inject
    StudentRepository studentRepository;
    
    @Inject
    AbsensiRepository absensiRepository;
    
    @Inject
    NilaiRepository nilaiRepository;
    
    @Inject
    PembayaranRepository pembayaranRepository;
    
    @Transactional
    public Student registerNewStudent(Student student) {
        studentRepository.persist(student);
        return student;
    }
    
    @Transactional
    public Student updateStudent(Student student) {
        if (student.id == null) {
            throw new IllegalArgumentException("Student ID cannot be null");
        }
        
        Student existingStudent = studentRepository.findById(student.id);
        if (existingStudent == null) {
            throw new IllegalArgumentException("Student not found with ID: " + student.id);
        }
        
        // Update student properties
        // Note: In a production app, you would have a more comprehensive update mechanism
        existingStudent.namaLengkap = student.namaLengkap;
        existingStudent.namaPanggilan = student.namaPanggilan;
        existingStudent.alamat = student.alamat;
        existingStudent.noTelepon = student.noTelepon;
        existingStudent.email = student.email;
        existingStudent.kelas = student.kelas;
        existingStudent.status = student.status;
        
        return existingStudent;
    }
    
    @Transactional
    public void deleteStudent(Long id) {
        Student student = studentRepository.findById(id);
        if (student == null) {
            throw new IllegalArgumentException("Student not found with ID: " + id);
        }
        
        // In real-world scenario, you might want to set status to inactive instead of deleting
        student.status = Student.StatusSiswa.TIDAK_AKTIF;
    }
    
    public List<Student> searchStudents(String searchTerm) {
        return studentRepository.findByName(searchTerm);
    }
    
    public Student getStudentByNisn(String nisn) {
        return Student.findByNisn(nisn);
    }
    
    public List<Student> getStudentsByClass(Kelas kelas) {
        return studentRepository.findByClass(kelas);
    }
    
    public double getStudentGradeAverage(Student student, TahunAjaran tahunAjaran, Semester semester) {
        List<Assessment> grades = nilaiRepository.findByStudentAndSubject(student, null, tahunAjaran, semester);
        
        return grades.stream()
                .mapToDouble(n -> n.nilaiAkhir)
                .average()
                .orElse(0);
    }
    
    public double getStudentAttendanceRate(Student student, TahunAjaran tahunAjaran, Semester semester) {
        return absensiRepository.calculateAttendancePercentage(student, tahunAjaran, semester);
    }
    
    public double getStudentOutstandingPayments(Student student, TahunAjaran tahunAjaran) {
        return pembayaranRepository.calculateTotalOutstandingByStudent(student, tahunAjaran);
    }
    
    @Transactional
    public void promoteStudent(Student student, Kelas targetKelas) {
        student.kelas = targetKelas;
    }
    
    @Transactional
    public void graduateStudent(Student student) {
        student.status = Student.StatusSiswa.LULUS;
        student.kelas = null;
    }
}
