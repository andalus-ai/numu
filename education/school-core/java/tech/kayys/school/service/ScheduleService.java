package tech.kayys.school.service;

import java.util.List;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;
import tech.kayys.school.model.JadwalPelajaran;
import tech.kayys.school.model.Kelas;
import tech.kayys.school.model.Teacher;

@ApplicationScoped
public class ScheduleService {
    
    @Transactional
    public JadwalPelajaran createScheduleEntry(JadwalPelajaran jadwal) {
        jadwal.persist();
        return jadwal;
    }
    
    @Transactional
    public void deleteScheduleEntry(Long id) {
        JadwalPelajaran jadwal = JadwalPelajaran.findById(id);
        if (jadwal == null) {
            throw new IllegalArgumentException("Schedule entry not found with ID: " + id);
        }
        
        jadwal.delete();
    }
    
    public List<JadwalPelajaran> getScheduleForClass(Kelas kelas) {
        return JadwalPelajaran.findByKelas(kelas);
    }
    
    public List<JadwalPelajaran> getScheduleForTeacher(Teacher guru) {
        return JadwalPelajaran.findByGuru(guru);
    }
    
    public List<JadwalPelajaran> getScheduleForDay(JadwalPelajaran.Hari hari) {
        return JadwalPelajaran.findByHari(hari);
    }
    
    public boolean checkScheduleConflict(JadwalPelajaran jadwal) {
        // Check if there's already a class scheduled at the same time
        List<JadwalPelajaran> existingSchedules = JadwalPelajaran.list(
            "kelas = ?1 and hari = ?2 and " +
            "((jamMulai <= ?3 and jamSelesai > ?3) or " +
            "(jamMulai < ?4 and jamSelesai >= ?4) or " +
            "(jamMulai >= ?3 and jamSelesai <= ?4))",
            jadwal.kelas, jadwal.hari, jadwal.jamMulai, jadwal.jamSelesai);
        
        if (!existingSchedules.isEmpty()) {
            return true;
        }
        
        // Check if the teacher is already assigned to another class at the same time
        List<JadwalPelajaran> teacherSchedules = JadwalPelajaran.list(
            "guru = ?1 and hari = ?2 and " +
            "((jamMulai <= ?3 and jamSelesai > ?3) or " +
            "(jamMulai < ?4 and jamSelesai >= ?4) or " +
            "(jamMulai >= ?3 and jamSelesai <= ?4))",
            jadwal.guru, jadwal.hari, jadwal.jamMulai, jadwal.jamSelesai);
        
        return !teacherSchedules.isEmpty();
    }
}
