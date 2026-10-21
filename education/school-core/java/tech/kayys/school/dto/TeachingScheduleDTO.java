package tech.kayys.school.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import tech.kayys.school.model.TeachingSchedule;

import java.time.DayOfWeek;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.TextStyle;
import java.util.Locale;

public class TeachingScheduleDTO {
    
    public Long id;
    
    @NotNull(message = "Hari harus diisi")
    public DayOfWeek day;
    
    public String dayName; // Indonesian day name
    
    @NotNull(message = "Waktu mulai harus diisi")
    public LocalTime startTime;
    
    @NotNull(message = "Waktu selesai harus diisi")
    public LocalTime endTime;
    
    public String timeRange; // Formatted time range
    
    @Min(value = 1, message = "Sesi minimal 1")
    @Max(value = 10, message = "Sesi maksimal 10") 
    @NotNull(message = "Sesi harus diisi")
    public Integer session;
    
    @NotNull(message = "Mata pelajaran harus diisi")
    public Long subjectId;
    
    public String subjectName;
    
    @NotNull(message = "Kelas harus diisi")
    public Long classroomId;
    
    public String classroomName;
    
    @NotNull(message = "Ruangan harus diisi")
    public Long roomId;
    
    public String roomName;
    
    public String teacherName;
    
    // Static methods for entity conversion
    public static TeachingScheduleDTO fromEntity(TeachingSchedule entity) {
        TeachingScheduleDTO dto = new TeachingScheduleDTO();
        dto.id = entity.id;
        dto.day = entity.day;
        dto.dayName = entity.day.getDisplayName(TextStyle.FULL, new Locale("id", "ID"));
        dto.startTime = entity.startTime;
        dto.endTime = entity.endTime;
        
        DateTimeFormatter timeFormatter = DateTimeFormatter.ofPattern("HH:mm");
        dto.timeRange = entity.startTime.format(timeFormatter) + " - " + entity.endTime.format(timeFormatter);
        
        dto.session = entity.session;
        
        if (entity.subject != null) {
            dto.subjectId = entity.subject.id;
            dto.subjectName = entity.subject.name;
            
            if (entity.subject.teacher != null) {
                dto.teacherName = entity.subject.teacher.fullName;
            }
        }
        
        if (entity.classroom != null) {
            dto.classroomId = entity.classroom.id;
            dto.classroomName = entity.classroom.name;
        }
        
        if (entity.room != null) {
            dto.roomId = entity.room.id;
            dto.roomName = entity.room.name;
        }
        
        return dto;
    }
    
    public TeachingSchedule toEntity() {
        TeachingSchedule entity = new TeachingSchedule();
        entity.id = this.id;
        entity.day = this.day;
        entity.startTime = this.startTime;
        entity.endTime = this.endTime;
        entity.session = this.session;
        
        return entity;
    }
}