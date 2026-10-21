package tech.kayys.school.model;

import java.util.HashSet;
import java.util.Set;

import io.quarkus.hibernate.orm.panache.PanacheEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;

@Entity
public class Course extends PanacheEntity {
    @Column(nullable = false)
    private String name;
    
    @Column(nullable = false)
    private String courseCode;
    
    @Column
    private String description;
    
    @ManyToOne
    private TeacherGeneral teacher;
    
    @ManyToMany(mappedBy = "enrolledCourses")
    private Set<StudentGeneral> students = new HashSet<>();
    
    @ManyToOne
    private AcademicYear academicYear;
    
    @Column(nullable = false) 
    private Integer maxCapacity;
    
    @Enumerated(EnumType.STRING)
    private CourseStatus status = CourseStatus.PENDING;
    
    @OneToMany(mappedBy = "course")
    private Set<Assignment> assignments = new HashSet<>();
    
    @OneToMany(mappedBy = "course")
    private Set<Attendance> attendanceRecords = new HashSet<>();
    
    public enum CourseStatus {
        PENDING, ACTIVE, COMPLETED, CANCELLED
    }
    
    // Getters and setters
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    
    public String getCourseCode() { return courseCode; }
    public void setCourseCode(String courseCode) { this.courseCode = courseCode; }
    
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    
    public TeacherGeneral getTeacher() { return teacher; }
    public void setTeacher(TeacherGeneral teacher) { this.teacher = teacher; }
    
    public Set<StudentGeneral> getStudents() { return students; }
    public void setStudents(Set<StudentGeneral> students) { this.students = students; }
    
    public AcademicYear getAcademicYear() { return academicYear; }
    public void setAcademicYear(AcademicYear academicYear) { this.academicYear = academicYear; }
    
    public Integer getMaxCapacity() { return maxCapacity; }
    public void setMaxCapacity(Integer maxCapacity) { this.maxCapacity = maxCapacity; }
    
    public CourseStatus getStatus() { return status; }
    public void setStatus(CourseStatus status) { this.status = status; }
    
    public Set<Assignment> getAssignments() { return assignments; }
    public void setAssignments(Set<Assignment> assignments) { this.assignments = assignments; }
    
    public boolean hasCapacity() {
        return students.size() < maxCapacity;
    }
}