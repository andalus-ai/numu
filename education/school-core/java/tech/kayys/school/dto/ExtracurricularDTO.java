package tech.kayys.school.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.DayOfWeek;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.TextStyle;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

public class ExtracurricularDTO {
    
    public Long id;
    
    @NotBlank(message = "Nama ekstrakurikuler harus diisi")
    public String name;
    
    public String description;
    
    @NotNull(message = "Hari harus diisi")
    public DayOfWeek day;
    
    public String dayName; // Indonesian day name
    
    @NotNull(message = "Waktu mulai harus diisi")
    public LocalTime startTime;
    
    @NotNull(message = "Waktu selesai harus diisi")
    public LocalTime endTime;
    
    public String timeRange; // Formatted time range
    
    public Long roomId;
    
    public String roomName;
    
    public List<TeacherSummaryDTO> supervisors;
    
    public Integer studentCount;
    
    public List<StudentSummaryDTO> students;
    
    // Static methods for entity conversion
    public static ExtracurricularDTO fromEntity(Extracurricular entity) {
        ExtracurricularDTO dto = new ExtracurricularDTO();
        dto.id = entity.id;
        dto.name = entity.name;
        dto.description = entity.description;
        dto.day = entity.day;
        dto.dayName = entity.day.getDisplayName(TextStyle.FULL, new Locale("id", "ID"));
        dto.startTime = entity.startTime;
        dto.endTime = entity.endTime;
        
        DateTimeFormatter timeFormatter = DateTimeFormatter.ofPattern("HH:mm");
        dto.timeRange = entity.startTime.format(timeFormatter) + " - " + entity.endTime.format(timeFormatter);
        
        if (entity.room != null) {
            dto.roomId = entity.room.id;
            dto.roomName = entity.room.name;
        }
        
        if (entity.supervisors != null) {
            dto.supervisors = entity.supervisors.stream()
                .map(TeacherSummaryDTO::fromEntity)
                .collect(Collectors.toList());
        }
        
        if (entity.students != null) {
            dto.studentCount = entity.students.size();
            dto.students = entity.students.stream()
                .map(StudentSummaryDTO::fromEntity)
                .collect(Collectors.toList());
        }
        
        return dto;
    }
    
    public Extracurricular toEntity() {
        Extracurricular entity = new Extracurricular();
        entity.id = this.id;
        entity.name = this.name;
        entity.description = this.description;
        entity.day = this.day;
        entity.startTime = this.startTime;
        entity.endTime = this.endTime;
        
        return entity;
    }
}
