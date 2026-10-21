package tech.kayys.school.model;

import java.util.HashSet;
import java.util.Set;

import io.quarkus.hibernate.orm.panache.PanacheEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.OneToMany;

@Entity
public class TeacherGeneral extends PanacheEntity {
    @Column(nullable = false)
    private String firstName;
    
    @Column(nullable = false)
    private String lastName;
    
    @Column(unique = true, nullable = false)
    private String employeeId;
    
    @Column(nullable = false)
    private String email;
    
    @Column(nullable = false)
    private String phoneNumber;
    
    @Column
    private String specialization;
    
    @OneToMany(mappedBy = "teacher")
    private Set<Course> coursesTeaching = new HashSet<>();
    
    // Getters and setters
    public String getFirstName() { return firstName; }
    public void setFirstName(String firstName) { this.firstName = firstName; }
    
    public String getLastName() { return lastName; }
    public void setLastName(String lastName) { this.lastName = lastName; }
    
    public String getEmployeeId() { return employeeId; }
    public void setEmployeeId(String employeeId) { this.employeeId = employeeId; }
    
    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }
    
    public String getPhoneNumber() { return phoneNumber; }
    public void setPhoneNumber(String phoneNumber) { this.phoneNumber = phoneNumber; }
    
    public String getSpecialization() { return specialization; }
    public void setSpecialization(String specialization) { this.specialization = specialization; }
    
    public Set<Course> getCoursesTeaching() { return coursesTeaching; }
    public void setCoursesTeaching(Set<Course> coursesTeaching) { this.coursesTeaching = coursesTeaching; }
    
    public void assignCourse(Course course) {
        coursesTeaching.add(course);
        course.setTeacher(this);
    }
    
    public void removeCourse(Course course) {
        coursesTeaching.remove(course);
        if (course.getTeacher() == this) {
            course.setTeacher(null);
        }
    }
}

