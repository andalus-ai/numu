package tech.kayys.school.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.List;
import java.util.stream.Collectors;

public class ClassroomDTO {
    
    public Long id;
    
    @NotBlank(message = "Nama kelas harus diisi")
    public String name;
    
    @Min(value = 1, message = "Kapasitas minimal 1")
    @Max(value = 40, message = "Kapasitas maksimal 40")
    @NotNull(message = "Kapasitas harus diisi")
    public Integer capacity;
    
    @NotNull(message = "Tahun ajaran harus diisi")
    public String academicYear;
    
    @NotNull(message = "Tingkat harus diisi")
    public Long gradeId;
    
    public String gradeName;
    
    public Long homeTeacherId;
    
    public String homeTeacherName;
    
    public Integer studentCount;
    
    public List<StudentSummaryDTO> students;
    
    // Static methods for entity conversion
    public static ClassroomDTO fromEntity(Classroom entity) {
        ClassroomDTO dto = new ClassroomDTO();
        dto.id = entity.id;
        dto.name = entity.name;
        dto.capacity = entity.capacity;
        dto.academicYear = entity.academicYear;
        
        if (entity.grade != null) {
            dto.gradeId = entity.grade.id;
            dto.gradeName = entity.grade.name;
        }
        
        if (entity.homeTeacher != null) {
            dto.homeTeacherId = entity.homeTeacher.id;
            dto.homeTeacherName = entity.homeTeacher.fullName;
        }
        
        if (entity.students != null) {
            dto.studentCount = entity.students.size();
            dto.students = entity.students.stream()
                .map(StudentSummaryDTO::fromEntity)
                .collect(Collectors.toList());
        }
        
        return dto;
    }
    
    public Classroom toEntity() {
        Classroom entity = new Classroom();
        entity.id = this.id;
        entity.name = this.name;
        entity.capacity = this.capacity;
        entity.academicYear = this.academicYear;
        
        return entity;
    }
}
