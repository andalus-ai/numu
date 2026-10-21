package tech.kayys.school.dto;


import jakarta.validation.constraints.*;
import tech.kayys.school.model.Student;

import java.time.LocalDate;

public class StudentDTO {
    
    public Long id;
    
    @NotBlank(message = "NISN harus diisi")
    @Pattern(regexp = "\\d{10}", message = "NISN harus terdiri dari 10 digit angka")
    public String nisn;
    
    @NotBlank(message = "NIS harus diisi")
    public String nis;
    
    @NotBlank(message = "Nama lengkap harus diisi")
    public String fullName;
    
    @NotNull(message = "Tanggal lahir harus diisi")
    @Past(message = "Tanggal lahir harus di masa lalu")
    public LocalDate dateOfBirth;
    
    @NotBlank(message = "Tempat lahir harus diisi")
    public String placeOfBirth;
    
    @NotNull(message = "Jenis kelamin harus diisi")
    public Student.Gender gender;
    
    @NotBlank(message = "Alamat harus diisi")
    public String address;
    
    @NotBlank(message = "Nomor telepon harus diisi")
    public String phoneNumber;
    
    public String email;
    
    public Boolean active = true;
    
    public LocalDate registrationDate;
    
    public Long classroomId;
    
    public String classroomName;
    
    public Long parentId;
    
    public String parentName;
    
    // Static methods for entity conversion
    public static StudentDTO fromEntity(Student entity) {
        StudentDTO dto = new StudentDTO();
        dto.id = entity.id;
        dto.nisn = entity.nisn;
        dto.nis = entity.nis;
        dto.fullName = entity.fullName;
        dto.dateOfBirth = entity.dateOfBirth;
        dto.placeOfBirth = entity.placeOfBirth;
        dto.gender = entity.gender;
        dto.address = entity.address;
        dto.phoneNumber = entity.phoneNumber;
        dto.email = entity.email;
        dto.active = entity.active;
        dto.registrationDate = entity.registrationDate;
        
        if (entity.classroom != null) {
            dto.classroomId = entity.classroom.id;
            dto.classroomName = entity.classroom.name;
        }
        
        if (entity.parent != null) {
            dto.parentId = entity.parent.id;
            dto.parentName = entity.parent.fatherName + " / " + entity.parent.motherName;
        }
        
        return dto;
    }
    
    public Student toEntity() {
        Student entity = new Student();
        entity.id = this.id;
        entity.nisn = this.nisn;
        entity.nis = this.nis;
        entity.fullName = this.fullName;
        entity.dateOfBirth = this.dateOfBirth;
        entity.placeOfBirth = this.placeOfBirth;
        entity.gender = this.gender;
        entity.address = this.address;
        entity.phoneNumber = this.phoneNumber;
        entity.email = this.email;
        entity.active = this.active;
        entity.registrationDate = this.registrationDate != null ? this.registrationDate : LocalDate.now();
        
        return entity;
    }
}
