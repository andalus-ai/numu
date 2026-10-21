package tech.kayys.school.repository;

import java.util.List;

import io.quarkus.hibernate.orm.panache.PanacheRepository;
import jakarta.enterprise.context.ApplicationScoped;
import tech.kayys.school.model.Kelas;
import tech.kayys.school.model.TahunAjaran;
import tech.kayys.school.model.Tingkat;

@ApplicationScoped
public class KelasRepository implements PanacheRepository<Kelas> {
    
    public List<Kelas> findByTahunAjaranAndTingkat(TahunAjaran tahunAjaran, Tingkat tingkat) {
        return list("tahunAjaran = ?1 and tingkat = ?2", tahunAjaran, tingkat);
    }
    
    public List<Kelas> findActiveByTingkat(Tingkat tingkat) {
        return list("tingkat = ?1 and status = ?2", tingkat, Kelas.StatusKelas.AKTIF);
    }
}
