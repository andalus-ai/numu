package tech.kayys.school.dto;

import tech.kayys.school.model.Student;

public class StudentSummaryDTO {
    public Long id;
    public String nisn;
    public String nis;
    public String fullName;
    public String classroomName;
    
    public static StudentSummaryDTO fromEntity(Student entity) {
        StudentSummaryDTO dto = new StudentSummaryDTO();
        dto.id = entity.id;
        dto.nisn = entity.nisn;
        dto.nis = entity.nis;
        dto.fullName = entity.fullName;
        
        if (entity.classroom != null) {
            dto.classroomName = entity.classroom.name;
        }
        
        return dto;
    }
}
