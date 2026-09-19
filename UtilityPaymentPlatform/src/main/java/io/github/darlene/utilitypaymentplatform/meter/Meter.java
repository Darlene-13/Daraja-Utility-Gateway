package io.github.darlene.utilitypaymentplatform.meter;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
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
    @Column(name = "meter_number", nullable = false, unique = true, length = 50)
    private String meterNumber;

    @Enumerated(EnumType.STRING)
    @Column(name = "meter_type", nullable = false, length = 20)
    private MeterType meterType;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "customer_id", nullable = false)
    private Customer customer;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @PrePersist
    void onCreate(){
        this.createdAt = OffsetDateTime.now();
    }
}