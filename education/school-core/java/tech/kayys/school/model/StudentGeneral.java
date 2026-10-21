package tech.kayys.school.model;


import io.quarkus.hibernate.orm.panache.PanacheEntity;

import java.time.LocalDate;
import java.util.HashSet;
import java.util.Set;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;

@Entity
public class StudentGeneral extends PanacheEntity {
    @Column(nullable = false)
    private String firstName;
    
    @Column(nullable = false)
    private String lastName;
    
    @Column(unique = true, nullable = false)
    private String studentId;
    
    @Column(nullable = false)
    private LocalDate dateOfBirth;
    
    @Column(nullable = false)
    private String email;
    
    @Column(nullable = false)
    private String phoneNumber;
    
    @Column(nullable = false)
    private String address;
    
    @ManyToOne
    private Grade currentGrade;
    
    @ManyToMany
    private Set<Course> enrolledCourses = new HashSet<>();
    
    @OneToMany(mappedBy = "student")
    private Set<Attendance> attendanceRecords = new HashSet<>();
    
    @OneToMany(mappedBy = "student")
    private Set<Grade> grades = new HashSet<>();
    
    @ManyToOne
    private Guardian primaryGuardian;

    // Getters and setters
    public String getFirstName() { return firstName; }
    public void setFirstName(String firstName) { this.firstName = firstName; }
    
    public String getLastName() { return lastName; }
    public void setLastName(String lastName) { this.lastName = lastName; }
    
    public String getStudentId() { return studentId; }
    public void setStudentId(String studentId) { this.studentId = studentId; }
    
    public LocalDate getDateOfBirth() { return dateOfBirth; }
    public void setDateOfBirth(LocalDate dateOfBirth) { this.dateOfBirth = dateOfBirth; }
    
    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }
    
    public String getPhoneNumber() { return phoneNumber; }
    public void setPhoneNumber(String phoneNumber) { this.phoneNumber = phoneNumber; }
    
    public String getAddress() { return address; }
    public void setAddress(String address) { this.address = address; }
    
    public Grade getCurrentGrade() { return currentGrade; }
    public void setCurrentGrade(Grade currentGrade) { this.currentGrade = currentGrade; }
    
    public Set<Course> getEnrolledCourses() { return enrolledCourses; }
    public void setEnrolledCourses(Set<Course> enrolledCourses) { this.enrolledCourses = enrolledCourses; }
    
    public void enrollCourse(Course course) {
        enrolledCourses.add(course);
        course.getStudents().add(this);
    }
    
    public void withdrawCourse(Course course) {
        enrolledCourses.remove(course);
        course.getStudents().remove(this);
    }
    
    public Guardian getPrimaryGuardian() { return primaryGuardian; }
    public void setPrimaryGuardian(Guardian primaryGuardian) { this.primaryGuardian = primaryGuardian; }
}

