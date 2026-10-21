package tech.kayys.school.service;

import java.util.List;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import tech.kayys.school.model.Kelas;
import tech.kayys.school.model.MataPelajaran;
import tech.kayys.school.model.Teacher;
import tech.kayys.school.repository.TeacherRepository;

@ApplicationScoped
public class TeacherService {
    
    @Inject
    TeacherRepository teacherRepository;
    
    @Transactional
    public Teacher registerNewTeacher(Teacher teacher) {
        teacherRepository.persist(teacher);
        return teacher;
    }
    
    @Transactional
    public Teacher updateTeacher(Teacher teacher) {
        if (teacher.id == null) {
            throw new IllegalArgumentException("Teacher ID cannot be null");
        }
        
        Teacher existingTeacher = teacherRepository.findById(teacher.id);
        if (existingTeacher == null) {
            throw new IllegalArgumentException("Teacher not found with ID: " + teacher.id);
        }
        
        // Update teacher properties
        existingTeacher.namaLengkap = teacher.namaLengkap;
        existingTeacher.alamat = teacher.alamat;
        existingTeacher.noTelepon = teacher.noTelepon;
        existingTeacher.email = teacher.email;
        existingTeacher.pendidikanTerakhir = teacher.pendidikanTerakhir;
        existingTeacher.mataPelajaranDiajar = teacher.mataPelajaranDiajar;
        existingTeacher.status = teacher.status;
        
        return existingTeacher;
    }
    
    @Transactional
    public void assignSubjectToTeacher(Teacher teacher, MataPelajaran mataPelajaran) {
        teacher.mataPelajaranDiajar.add(mataPelajaran);
    }
    
    @Transactional
    public void assignHomeRoomTeacher(Teacher teacher, Kelas kelas) {
        // Check if the class already has a homeroom teacher
        if (kelas.waliKelas != null) {
            kelas.waliKelas.kelasWali = null;
        }
        
        teacher.kelasWali = kelas;
        kelas.waliKelas = teacher;
    }
    
    public List<Teacher> findTeachersBySubject(MataPelajaran mataPelajaran) {
        return teacherRepository.findBySubject(mataPelajaran);
    }
    
    public List<Teacher> findHomeRoomTeachers() {
        return teacherRepository.findHomeRoomTeachers();
    }
}
