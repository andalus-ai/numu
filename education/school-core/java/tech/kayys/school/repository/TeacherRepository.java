package tech.kayys.school.repository;

import java.util.List;

import io.quarkus.hibernate.orm.panache.PanacheRepository;
import jakarta.enterprise.context.ApplicationScoped;
import tech.kayys.school.model.MataPelajaran;
import tech.kayys.school.model.Teacher;

@ApplicationScoped
public class TeacherRepository implements PanacheRepository<Teacher> {
    
    public List<Teacher> findByName(String name) {
        return list("namaLengkap like ?1", "%" + name + "%");
    }
    
    public List<Teacher> findBySubject(MataPelajaran mataPelajaran) {
        return find("mataPelajaranDiajar", mataPelajaran).list();
    }
    
    public List<Teacher> findHomeRoomTeachers() {
        return find("kelasWali is not null").list();
    }
}

