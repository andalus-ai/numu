package tech.kayys.school.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import tech.kayys.school.model.Grade;

import java.util.List;
import java.util.stream.Collectors;

public class GradeDTO {
    
    public Long id;
    
    @NotBlank(message = "Kode tingkat harus diisi")
    public String code;
    
    @NotBlank(message = "Nama tingkat harus diisi")
    public String name;
    
    @Min(value = 1, message = "Tingkat minimal 1")
    @Max(value = 12, message = "Tingkat maksimal 12")
    @NotNull(message = "Nomor tingkat harus diisi")
    public Integer level;
    
    @NotNull(message = "Jenis pendidikan harus diisi")
    public Grade.EducationType educationType;
    
    public List<String> classrooms;
    
    // Static methods for entity conversion
    public static GradeDTO fromEntity(Grade entity) {
        GradeDTO dto = new GradeDTO();
        dto.id = entity.id;
        dto.code = entity.code;
        dto.name = entity.name;
        dto.level = entity.level;
        dto.educationType = entity.educationType;
        
        if (entity.classrooms != null) {
            dto.classrooms = entity.classrooms.stream()
                .map(classroom -> classroom.name)
                .collect(Collectors.toList());
        }
        
        return dto;
    }
    
    public Grade toEntity() {
        Grade entity = new Grade();
        entity.id = this.id;
        entity.code = this.code;
        entity.name = this.name;
        entity.level = this.level;
        entity.educationType = this.educationType;
        
        return entity;
    }
}
