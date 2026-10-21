package tech.kayys.school.model;


import io.quarkus.hibernate.orm.panache.PanacheEntity;
import jakarta.persistence.*;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "rooms")
public class Room extends PanacheEntity {
    
    @NotBlank(message = "Kode ruangan harus diisi")
    @Column(unique = true)
    public String code;
    
    @NotBlank(message = "Nama ruangan harus diisi")
    public String name;
    
    @Min(value = 1, message = "Kapasitas minimal 1")
    @Max(value = 100, message = "Kapasitas maksimal 100")
    @NotNull(message = "Kapasitas harus diisi")
    public Integer capacity;
    
    @NotNull(message = "Jenis ruangan harus diisi")
    @Enumerated(EnumType.STRING)
    public RoomType type;
    
    @NotNull(message = "Status ruangan harus diisi")
    @Enumerated(EnumType.STRING)
    public RoomStatus status;
    
    @OneToMany(mappedBy = "room")
    public List<TeachingSchedule> schedules = new ArrayList<>();
    
    public enum RoomType {
        KELAS, LABORATORIUM, PERPUSTAKAAN, AULA, KANTOR, LAINNYA
    }
    
    public enum RoomStatus {
        AKTIF, PERBAIKAN, TIDAK_AKTIF
    }
}
