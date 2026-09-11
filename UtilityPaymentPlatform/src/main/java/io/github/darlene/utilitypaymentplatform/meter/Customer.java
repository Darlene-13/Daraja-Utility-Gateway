package io.github.darlene.utilitypaymentplatform.meter;


import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import org.hibernate.validator.constraints.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

import java.time.OffsetDateTime;


@Entity(name = "customers")
@Builder
@AllArgsConstructor
@Getter
@Setter
public class Customer{


    @Id
    @UUID
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private UUID id;

    @Column(name = "full-name", nullable = false)
    @NotNull
    private String fullName;

    @Column(name = "phone-number", nullable = false, unique = true, length = 15)
    private String phoneNumber;

    @CreationTimestamp
    @Column(name = "created-at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @PrePersist
    void onCreate(){
        this.createdAt = OffsetDateTime.now();
    }
}