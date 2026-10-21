package tech.kayys.school.model;


import io.quarkus.hibernate.orm.panache.PanacheEntity;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "parents")
public class Parent extends PanacheEntity {
    
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
    @Enumerated(EnumType.STRING)
    public EconomicStatus economicStatus;
    
    @OneToMany(mappedBy = "parent")
    public List<Student> children = new ArrayList<>();
    
    public enum EconomicStatus {
        PRASEJAHTERA, SEJAHTERA_1, SEJAHTERA_2, SEJAHTERA_3, SEJAHTERA_PLUS
    }
}
