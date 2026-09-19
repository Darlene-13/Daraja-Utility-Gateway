package io.github.darlene.utilitypaymentplatform.payment.domain;

import io.github.darlene.utilitypaymentplatform.meter.Customer;
import io.github.darlene.utilitypaymentplatform.meter.Meter;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.validator.constraints.UUID;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

@Entity
@Table(name = "transactions",
                indexes = {
                        @Index(name = "idx_transactions_status", columnList = "status"),
                        @Index(name = "idx_transactions_customer_id", columnList = "customer_id")
                })
@AllArgsConstructor
@Getter @Setter
public class Transaction {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "meter_id", unique = true, nullable = false)
    private Meter meter;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "customer_id", nullable = false, unique = true)
    private Customer customer;

    @Column(name = "amount", nullable = false, precision = 12, scale = 2)
    @NotNull
    private BigDecimal amount;

    @Column(name = "phone_number", nullable = false, unique = true)
    @NotNull
    private String phoneNumber;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    private Status status;

    @Column(name = "merchant_request_id", length = 50)
    private String merchantRequestId;

    @Column(name = "checkout_request_id", nullable = false, unique = true, length=50)
    private String checkoutRequestId;

    @Column(name = "correlation_id", nullable = false, unique = true)
    private UUID correlationId;

    @Column(name = "token", nullable = false, unique = true, length = 50)
    private String token;

    @Column(name = "failure_reason", nullable = false, length = 255)
    private String failureReason;

    // Flips to true only when retries are exhausted and a human needs to look
    // at this row, this is the logical dead letter, there is no separate
    // dead letter table.
    @Column(name = "needs_review", nullable = false)
    private boolean needsReview = false;


    @Version
    @Column(name = "version", nullable = false)
    @NotNull
    private String version;

    public Transaction() {

    }

    @PrePersist
    void onCreate(){
        @NotNull OffsetDateTime createdAt = OffsetDateTime.now();
        @NotNull OffsetDateTime updatedAt = OffsetDateTime.now();
    }

    @PreUpdate
    void onUpdate(){
        @NotNull OffsetDateTime updatedAt = OffsetDateTime.now();
    }
}
