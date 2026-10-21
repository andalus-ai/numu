package tech.kayys.school.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public class RoomDTO {
    
    public Long id;
    
    @NotBlank(message = "Kode ruangan harus diisi")
    public String code;
    
    @NotBlank(message = "Nama ruangan harus diisi")
    public String name;
    
    @Min(value = 1, message = "Kapasitas minimal 1")
    @Max(value = 100, message = "Kapasitas maksimal 100")
    @NotNull(message = "Kapasitas harus diisi")
    public Integer capacity;
    
    @NotNull(message = "Jenis ruangan harus diisi")
    public Room.RoomType type;
    
    @NotNull(message = "Status ruangan harus diisi")
    public Room.RoomStatus status;
    
    public Integer scheduleCount;
    
    // Static methods for entity conversion
    public static RoomDTO fromEntity(Room entity) {
        RoomDTO dto = new RoomDTO();
        dto.id = entity.id;
        dto.code = entity.code;
        dto.name = entity.name;
        dto.capacity = entity.capacity;
        dto.type = entity.type;
        dto.status = entity.status;
        
        if (entity.schedules != null) {
            dto.scheduleCount = entity.schedules.size();
        }
        
        return dto;
    }
    
    public Room toEntity() {
        Room entity = new Room();
        entity.id = this.id;
        entity.code = this.code;
        entity.name = this.name;
        entity.capacity = this.capacity;
        entity.type = this.type;
        entity.status = this.status;
        
        return entity;
    }
}
