package tech.kayys.school.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import tech.kayys.school.model.Parent;

import java.util.List;
import java.util.stream.Collectors;

public class ParentDTO {
    
    public Long id;
    
    @NotBlank(message = "Nama ayah harus diisi")
    public String fatherName;
    
    public String fatherOccupation;
    
    public String fatherPhoneNumber;
    
    @NotBlank(message = "Nama ibu harus diisi")
    public String motherName;
    
    public String motherOccupation;
    
    public String motherPhoneNumber;
    
    @NotBlank(message = "Alamat harus diisi")
    public String address;
    
    @NotNull(message = "Status ekonomi harus diisi")
    public Parent.EconomicStatus economicStatus;
    
    public List<StudentSummaryDTO> children;
    
    // Static methods for entity conversion
    public static ParentDTO fromEntity(Parent entity) {
        ParentDTO dto = new ParentDTO();
        dto.id = entity.id;
        dto.fatherName = entity.fatherName;
        dto.fatherOccupation = entity.fatherOccupation;
        dto.fatherPhoneNumber = entity.fatherPhoneNumber;
        dto.motherName = entity.motherName;
        dto.motherOccupation = entity.motherOccupation;
        dto.motherPhoneNumber = entity.motherPhoneNumber;
        dto.address = entity.address;
        dto.economicStatus = entity.economicStatus;
        
        if (entity.children != null) {
            dto.children = entity.children.stream()
                .map(StudentSummaryDTO::fromEntity)
                .collect(Collectors.toList());
        }
        
        return dto;
    }
    
    public Parent toEntity() {
        Parent entity = new Parent();
        entity.id = this.id;
        entity.fatherName = this.fatherName;
        entity.fatherOccupation = this.fatherOccupation;
        entity.fatherPhoneNumber = this.fatherPhoneNumber;
        entity.motherName = this.motherName;
        entity.motherOccupation = this.motherOccupation;
        entity.motherPhoneNumber = this.motherPhoneNumber;
        entity.address = this.address;
        entity.economicStatus = this.economicStatus;
        
        return entity;
    }
}
