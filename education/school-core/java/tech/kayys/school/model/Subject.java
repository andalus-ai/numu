package tech.kayys.school.model;


import io.quarkus.hibernate.orm.panache.PanacheEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "subjects")
public class Subject extends PanacheEntity {
    
    @NotBlank(message = "Kode mata pelajaran harus diisi")
    @Column(unique = true)
    public String code;
    
    @NotBlank(message = "Nama mata pelajaran harus diisi")
    public String name;
    
    @Min(value = 1, message = "Jumlah jam pelajaran minimal 1")
    @Max(value = 10, message = "Jumlah jam pelajaran maksimal 10")
    @NotNull(message = "Jumlah jam pelajaran harus diisi")
    public Integer hoursPerWeek;
    
    @NotNull(message = "Kurikulum harus diisi")
    @Enumerated(EnumType.STRING)
    public Curriculum curriculum;
    
    @ManyToOne
    @JoinColumn(name = "teacher_id")
    public Teacher teacher;
    
    @OneToMany(mappedBy = "subject")
    public List<TeachingSchedule> schedules = new ArrayList<>();
    
    @ManyToMany
    @JoinTable(
        name = "subject_grade",
        joinColumns = @JoinColumn(name = "subject_id"),
        inverseJoinColumns = @JoinColumn(name = "grade_id")
    )
    public List<Grade> grades = new ArrayList<>();
    
    public enum Curriculum {
        KTSP, K13, MERDEKA_BELAJAR
    }
}
