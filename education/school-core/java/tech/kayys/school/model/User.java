package tech.kayys.school.model;


import io.quarkus.hibernate.orm.panache.PanacheEntity;
import io.quarkus.elytron.security.common.BcryptUtil;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDateTime;

@Entity
@Table(name = "users")
public class User extends PanacheEntity {
    
    @NotBlank(message = "Username harus diisi")
    @Column(unique = true)
    public String username;
    
    @NotBlank(message = "Password harus diisi")
    public String password;
    
    @NotNull(message = "Peran harus diisi")
    @Enumerated(EnumType.STRING)
    public Role role;
    
    @NotNull(message = "Status aktif harus diisi")
    public Boolean active = true;
    
    public LocalDateTime lastLogin;
    
    @NotNull(message = "Tanggal pembuatan harus diisi")
    public LocalDateTime createdAt = LocalDateTime.now();
    
    // Reference to related entity based on role
    public Long entityId; // ID of student, teacher, parent or staff
    
    public void setPassword(String password) {
        this.password = BcryptUtil.bcryptHash(password);
    }
    
    public boolean verifyPassword(String password) {
        return BcryptUtil.matches(password, this.password);
    }
    
    public enum Role {
        ADMIN, TEACHER, STUDENT, PARENT, STAFF
    }
}

