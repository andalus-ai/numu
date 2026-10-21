package tech.kayys.school.model;


import io.quarkus.hibernate.orm.panache.PanacheEntity;
import jakarta.persistence.*;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.time.DayOfWeek;
import java.time.LocalTime;

@Entity
@Table(name = "teaching_schedules")
public class TeachingSchedule extends PanacheEntity {
    
    @NotNull(message = "Hari harus diisi")
    @Enumerated(EnumType.STRING)
    public DayOfWeek day;
    
    @NotNull(message = "Waktu mulai harus diisi")
    public LocalTime startTime;
    
    @NotNull(message = "Waktu selesai harus diisi")
    public LocalTime endTime;
    
    @Min(value = 1, message = "Sesi minimal 1")
    @Max(value = 10, message = "Sesi maksimal 10") 
    @NotNull(message = "Sesi harus diisi")
    public Integer session;
    
    @ManyToOne
    @JoinColumn(name = "subject_id")
    @NotNull(message = "Mata pelajaran harus diisi")
    public Subject subject;
    
    @ManyToOne
    @JoinColumn(name = "classroom_id")
    @NotNull(message = "Kelas harus diisi")
    public Kelas classroom;
    
    @ManyToOne
    @JoinColumn(name = "room_id")
    @NotNull(message = "Ruangan harus diisi")
    public Room room;
}

