package tech.kayys.school.model;

import java.time.LocalDate;
import java.util.ArrayList;

import io.quarkus.hibernate.orm.panache.PanacheEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

@Entity
public class Grade extends PanacheEntity {
    @ManyToOne
    private Student student;
    
    @ManyToOne
    private Course course;
    
    @Column(nullable = false)
    private Double score;
    
    @Enumerated(EnumType.STRING)
    private GradeType type;
    
    @Column
    private String comments;
    
    @Column(nullable = false)
    private LocalDate gradeDate;
    
    public enum GradeType {
        ASSIGNMENT, QUIZ, MIDTERM, FINAL, PROJECT
    }
    
    // Getters and setters
    public Student getStudent() { return student; }
    public void setStudent(Student student) { this.student = student; }
    
    public Course getCourse() { return course; }
    public void setCourse(Course course) { this.course = course; }
    
    public Double getScore() { return score; }
    public void setScore(Double score) { this.score = score; }
    
    public GradeType getType() { return type; }
    public void setType(GradeType type) { this.type = type; }
    
    public String getComments() { return comments; }
    public void setComments(String comments) { this.comments = comments; }
    
    public LocalDate getGradeDate() { return gradeDate; }
    public void setGradeDate(LocalDate gradeDate) { this.gradeDate = gradeDate; }
    
    public String getLetterGrade() {
        if (score >= 90) return "A";
        else if (score >= 80) return "B";
        else if (score >= 70) return "C";
        else if (score >= 60) return "D";
        else return "F";
    }


    @NotBlank(message = "Kode tingkat harus diisi")
    @Column(unique = true)
    public String code;
    
    @NotBlank(message = "Nama tingkat harus diisi")
    public String name;
    
    @Min(value = 1, message = "Tingkat minimal 1")
    @Max(value = 12, message = "Tingkat maksimal 12")
    @NotNull(message = "Nomor tingkat harus diisi")
    public Integer level;
    
    @NotNull(message = "Jenis pendidikan harus diisi")
    @Enumerated(EnumType.STRING)
    public EducationType educationType;
    
    @OneToMany(mappedBy = "grade")
    public List<Kelas> classrooms = new ArrayList<>();
    
    @ManyToMany(mappedBy = "grades")
    public List<Subject> subjects = new ArrayList<>();
    
    public enum EducationType {
        SD, SMP, SMA, SMK
    }
}
