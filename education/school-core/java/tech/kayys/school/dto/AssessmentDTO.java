package tech.kayys.school.dto;

import jakarta.validation.constraints.*;
import tech.kayys.school.model.Assessment;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

public class AssessmentDTO {
    
    public Long id;
    
    @NotNull(message = "Siswa harus diisi")
    public Long studentId;
    
    public String studentName;
    
    public String studentNisn;
    
    public String classroomName;
    
    @NotNull(message = "Mata pelajaran harus diisi")
    public Long subjectId;
    
    public String subjectName;
    
    @NotNull(message = "Jenis penilaian harus diisi")
    public Assessment.AssessmentType type;
    
    @NotBlank(message = "Judul penilaian harus diisi")
    public String title;
    
    @NotNull(message = "Tanggal penilaian harus diisi")
    public LocalDate date;
    
    public String formattedDate;
    
    @Min(value = 0, message = "Nilai minimal 0")
    @Max(value = 100, message = "Nilai maksimal 100")
    @NotNull(message = "Nilai harus diisi")
    public Double score;
    
    @NotNull(message = "Semester harus diisi")
    public Assessment.Semester semester;
    
    public String notes;
    
    public String letterGrade; // A, B, C, D, E
    
    // Static methods for entity conversion
    public static AssessmentDTO fromEntity(Assessment entity) {
        AssessmentDTO dto = new AssessmentDTO();
        dto.id = entity.id;
        dto.type = entity.type;
        dto.title = entity.title;
        dto.date = entity.date;
        dto.formattedDate = entity.date.format(DateTimeFormatter.ofPattern("dd MMMM yyyy"));
        dto.score = entity.score;
        dto.semester = entity.semester;
        dto.notes = entity.notes;
        
        // Calculate letter grade based on score
        if (entity.score >= 85) {
            dto.letterGrade = "A";
        } else if (entity.score >= 75) {
            dto.letterGrade = "B";
        } else if (entity.score >= 60) {
            dto.letterGrade = "C";
        } else if (entity.score >= 40) {
            dto.letterGrade = "D";
        } else {
            dto.letterGrade = "E";
        }
        
        if (entity.student != null) {
            dto.studentId = entity.student.id;
            dto.studentName = entity.student.fullName;
            dto.studentNisn = entity.student.nisn;
            
            if (entity.student.classroom != null) {
                dto.classroomName = entity.student.classroom.name;
            }
        }
        
        if (entity.subject != null) {
            dto.subjectId = entity.subject.id;
            dto.subjectName = entity.subject.name;
        }
        
        return dto;
    }
    
    public Assessment toEntity() {
        Assessment entity = new Assessment();
        entity.id = this.id;
        entity.type = this.type;
        entity.title = this.title;
        entity.date = this.date;
        entity.score = this.score;
        entity.semester = this.semester;
        entity.notes = this.notes;
        
        return entity;
    }
}
