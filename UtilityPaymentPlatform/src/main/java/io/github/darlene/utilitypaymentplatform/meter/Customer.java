package io.github.darlene.utilitypaymentplatform.meter;


import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.OffsetDateTime;

@Entity
@Table(name = "customers")
@Builder
@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
public class Customer{


    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "full_name", nullable = false)
    @NotNull
    private String fullName;

    @Column(name = "phone_number", nullable = false, unique = true, length = 15)
    private String phoneNumber;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @PrePersist
    void onCreate(){
        this.createdAt = OffsetDateTime.now();
    }
}