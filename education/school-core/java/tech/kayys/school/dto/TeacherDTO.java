package tech.kayys.school.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Past;
import jakarta.validation.constraints.Pattern;
import tech.kayys.school.model.Student;
import tech.kayys.school.model.Teacher;

import java.time.LocalDate;
import java.util.List;
import java.util.stream.Collectors;

public class TeacherDTO {
    
    public Long id;
    
    @NotBlank(message = "NIP harus diisi")
    @Pattern(regexp = "\\d{18}", message = "NIP harus terdiri dari 18 digit angka")
    public String nip;
    
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
    
    @NotNull(message = "Tanggal mulai mengajar harus diisi")
    public LocalDate startDate;
    
    @NotNull(message = "Pendidikan terakhir harus diisi")
    public Teacher.Education education;
    
    @NotBlank(message = "Bidang studi harus diisi")
    public String fieldOfStudy;
    
    @NotNull(message = "Status kepegawaian harus diisi")
    public Teacher.EmploymentStatus employmentStatus;
    
    public List<String> subjects;
    
    public String homeClassroomName;
    
    // Static methods for entity conversion
    public static TeacherDTO fromEntity(Teacher entity) {
        TeacherDTO dto = new TeacherDTO();
        dto.id = entity.id;
        dto.nip = entity.nip;
        dto.fullName = entity.fullName;
        dto.dateOfBirth = entity.dateOfBirth;
        dto.placeOfBirth = entity.placeOfBirth;
        dto.gender = entity.gender;
        dto.address = entity.address;
        dto.phoneNumber = entity.phoneNumber;
        dto.email = entity.email;
        dto.startDate = entity.startDate;
        dto.education = entity.education;
        dto.fieldOfStudy = entity.fieldOfStudy;
        dto.employmentStatus = entity.employmentStatus;
        
        if (entity.subjects != null) {
            dto.subjects = entity.subjects.stream()
                .map(subject -> subject.name)
                .collect(Collectors.toList());
        }
        
        if (entity.classroom != null) {
            dto.homeClassroomName = entity.classroom.name;
        }
        
        return dto;
    }
    
    public Teacher toEntity() {
        Teacher entity = new Teacher();
        entity.id = this.id;
        entity.nip = this.nip;
        entity.fullName = this.fullName;
        entity.dateOfBirth = this.dateOfBirth;
        entity.placeOfBirth = this.placeOfBirth;
        entity.gender = this.gender;
        entity.address = this.address;
        entity.phoneNumber = this.phoneNumber;
        entity.email = this.email;
        entity.startDate = this.startDate;
        entity.education = this.education;
        entity.fieldOfStudy = this.fieldOfStudy;
        entity.employmentStatus = this.employmentStatus;
        
        return entity;
    }
}