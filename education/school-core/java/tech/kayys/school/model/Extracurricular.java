package tech.kayys.school.model;


import io.quarkus.hibernate.orm.panache.PanacheEntity;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.HashSet;
import java.util.Set;

@Entity
@Table(name = "extracurriculars")
public class Extracurricular extends PanacheEntity {
    
    @NotBlank(message = "Nama ekstrakurikuler harus diisi")
    public String name;
    
    public String description;
    
    @NotNull(message = "Hari harus diisi")
    @Enumerated(EnumType.STRING)
    public DayOfWeek day;
    
    @NotNull(message = "Waktu mulai harus diisi")
    public LocalTime startTime;
    
    @NotNull(message = "Waktu selesai harus diisi")
    public LocalTime endTime;
    
    @ManyToOne
    @JoinColumn(name = "room_id")
    public Room room;
    
    @ManyToMany
    @JoinTable(
        name = "extracurricular_supervisor",
        joinColumns = @JoinColumn(name = "extracurricular_id"),
        inverseJoinColumns = @JoinColumn(name = "teacher_id")
    )
    public Set<Teacher> supervisors = new HashSet<>();
    
    @ManyToMany
    @JoinTable(
        name = "extracurricular_student",
        joinColumns = @JoinColumn(name = "extracurricular_id"),
        inverseJoinColumns = @JoinColumn(name = "student_id")
    )
    public Set<Student> students = new HashSet<>();
}

