package tech.kayys.school.dto;

import tech.kayys.school.model.Teacher;

public class TeacherSummaryDTO {
    public Long id;
    public String nip;
    public String fullName;
    public String fieldOfStudy;
    
    public static TeacherSummaryDTO fromEntity(Teacher entity) {
        TeacherSummaryDTO dto = new TeacherSummaryDTO();
        dto.id = entity.id;
        dto.nip = entity.nip;
        dto.fullName = entity.fullName;
        dto.fieldOfStudy = entity.fieldOfStudy;
        
        return dto;
    }
}
