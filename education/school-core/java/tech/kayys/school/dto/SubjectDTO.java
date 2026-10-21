package tech.kayys.school.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import tech.kayys.school.model.Subject;

import java.util.List;
import java.util.stream.Collectors;

public class SubjectDTO {
    
    public Long id;
    
    @NotBlank(message = "Kode mata pelajaran harus diisi")
    public String code;
    
    @NotBlank(message = "Nama mata pelajaran harus diisi")
    public String name;
    
    @Min(value = 1, message = "Jumlah jam pelajaran minimal 1")
    @Max(value = 10, message = "Jumlah jam pelajaran maksimal 10")
    @NotNull(message = "Jumlah jam pelajaran harus diisi")
    public Integer hoursPerWeek;
    
    @NotNull(message = "Kurikulum harus diisi")
    public Subject.Curriculum curriculum;
    
    public Long teacherId;
    
    public String teacherName;
    
    public List<String> grades;
    
    // Static methods for entity conversion
    public static SubjectDTO fromEntity(Subject entity) {
        SubjectDTO dto = new SubjectDTO();
        dto.id = entity.id;
        dto.code = entity.code;
        dto.name = entity.name;
        dto.hoursPerWeek = entity.hoursPerWeek;
        dto.curriculum = entity.curriculum;
        
        if (entity.teacher != null) {
            dto.teacherId = entity.teacher.id;
            dto.teacherName = entity.teacher.fullName;
        }
        
        if (entity.grades != null) {
            dto.grades = entity.grades.stream()
                .map(grade -> grade.name)
                .collect(Collectors.toList());
        }
        
        return dto;
    }
    
    public Subject toEntity() {
        Subject entity = new Subject();
        entity.id = this.id;
        entity.code = this.code;
        entity.name = this.name;
        entity.hoursPerWeek = this.hoursPerWeek;
        entity.curriculum = this.curriculum;
        
        return entity;
    }
}
