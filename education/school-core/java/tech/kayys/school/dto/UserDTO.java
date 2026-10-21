package tech.kayys.school.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import tech.kayys.school.model.User;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public class UserDTO {
    
    public Long id;
    
    @NotBlank(message = "Username harus diisi")
    public String username;
    
    @NotBlank(message = "Password harus diisi")
    public String password;
    
    @NotNull(message = "Peran harus diisi")
    public User.Role role;
    
    @NotNull(message = "Status aktif harus diisi")
    public Boolean active = true;
    
    public LocalDateTime lastLogin;
    
    public String formattedLastLogin;
    
    public LocalDateTime createdAt;
    
    public String formattedCreatedAt;
    
    public Long entityId; // ID of student, teacher, parent or staff
    
    public String entityName; // Name of the related entity
    
    // Static methods for entity conversion
    public static UserDTO fromEntity(User entity) {
        UserDTO dto = new UserDTO();
        dto.id = entity.id;
        dto.username = entity.username;
        // Don't return password
        dto.role = entity.role;
        dto.active = entity.active;
        dto.lastLogin = entity.lastLogin;
        dto.createdAt = entity.createdAt;
        dto.entityId = entity.entityId;
        
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd MMM yyyy HH:mm:ss");
        if (entity.lastLogin != null) {
            dto.formattedLastLogin = entity.lastLogin.format(formatter);
        }
        if (entity.createdAt != null) {
            dto.formattedCreatedAt = entity.createdAt.format(formatter);
        }
        
        return dto;
    }
    
    public User toEntity() {
        User entity = new User();
        entity.id = this.id;
        entity.username = this.username;
        if (this.password != null && !this.password.isEmpty()) {
            entity.setPassword(this.password);
        }
        entity.role = this.role;
        entity.active = this.active;
        entity.lastLogin = this.lastLogin;
        entity.createdAt = this.createdAt != null ? this.createdAt : LocalDateTime.now();
        entity.entityId = this.entityId;
        
        return entity;
    }
}
