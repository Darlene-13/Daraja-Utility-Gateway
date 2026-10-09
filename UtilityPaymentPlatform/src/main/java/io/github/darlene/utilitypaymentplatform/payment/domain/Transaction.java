package io.github.darlene.utilitypaymentplatform.payment.domain;

import io.github.darlene.utilitypaymentplatform.meter.Customer;
import io.github.darlene.utilitypaymentplatform.meter.Meter;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;
import java.util.UUID;

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
    @JoinColumn(name = "meter_id", nullable = false)
    private Meter meter;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "customer_id", nullable = false)
    private Customer customer;

    @Column(name = "amount", nullable = false, precision = 12, scale = 2)
    @NotNull
    private BigDecimal amount;

    @Column(name = "phone_number", nullable = false)
    @NotNull
    private String phoneNumber;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    private Status status;

    @Column(name = "merchant_request_id", length = 50)
    private String merchantRequestId;

    @Column(name = "checkout_request_id", unique = true, length=50)
    private String checkoutRequestId;

    @Column(name = "correlation_id", unique = true)
    private UUID correlationId;

    @Column(name = "token", nullable = false, unique = true, length = 50)
    private String token;

    @Column(name = "failure_reason", length = 255)
    private String failureReason;

    // Flips to true only when retries are exhausted and a human needs to look
    // at this row, this is the logical dead letter, there is no separate
    // dead letter table.
    @Column(name = "needs_review", nullable = false)
    private boolean needsReview = false;


    @Version
    @Column(name = "version", nullable = false)
    @NotNull
    private int version;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    public Transaction() {

    }

    @PrePersist
    void onCreate(){
        OffsetDateTime now = OffsetDateTime.now();
        this.createdAt = now;
        this.updatedAt = now;
    }

    @PreUpdate
    void onUpdate(){
        this.updatedAt = OffsetDateTime.now();
    }
}
