package tech.kayys.school.dto;

import jakarta.validation.constraints.NotNull;
import tech.kayys.school.model.Attendance;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

public class AttendanceDTO {
    
    public Long id;
    
    @NotNull(message = "Siswa harus diisi")
    public Long studentId;
    
    public String studentName;
    
    public String studentNisn;
    
    public String classroomName;
    
    @NotNull(message = "Tanggal harus diisi")
    public LocalDate date;
    
    public String formattedDate;
    
    @NotNull(message = "Status kehadiran harus diisi")
    public Attendance.AttendanceStatus status;
    
    public String notes;
    
    // Static methods for entity conversion
    public static AttendanceDTO fromEntity(Attendance entity) {
        AttendanceDTO dto = new AttendanceDTO();
        dto.id = entity.id;
        dto.date = entity.date;
        dto.formattedDate = entity.date.format(DateTimeFormatter.ofPattern("dd MMMM yyyy"));
        dto.status = entity.status;
        dto.notes = entity.notes;
        
        if (entity.student != null) {
            dto.studentId = entity.student.id;
            dto.studentName = entity.student.fullName;
            dto.studentNisn = entity.student.nisn;
            
            if (entity.student.classroom != null) {
                dto.classroomName = entity.student.classroom.name;
            }
        }
        
        return dto;
    }
    
    public Attendance toEntity() {
        Attendance entity = new Attendance();
        entity.id = this.id;
        entity.date = this.date;
        entity.status = this.status;
        entity.notes = this.notes;
        
        return entity;
    }
}
