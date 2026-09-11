package io.github.darlene.utilitypaymentplatform.meter;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.*;
import org.hibernate.validator.constraints.UUID;
import org.hibernate.annotations.CreationTimestamp;

import java.time.OffsetDateTime;

@Entity(name = "meter")
@Getter @Setter @AllArgsConstructor



public class Meter{

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @UUID
    private UUID id;

    @Column(name = "meter-number", nullable = false, unique = true)
    @NotBlank
    private String meterNumber;

    @Column(name = "meter-type", nullable = false, unique = true)
    @NotBlank
    private MeterType meterType;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @PrePersist
    void onCreate(){
        this.createdAt = OffsetDateTime.now();
    }
}