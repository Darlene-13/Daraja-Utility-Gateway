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

    // Null until the token worker has issued a token for this payment
    @Column(name = "token", unique = true, length = 50)
    private String token;

    @Column(name = "token_units", precision = 12, scale = 2)
    private BigDecimal tokenUnits;

    @Column(name = "token_provider_reference", length = 100)
    private String tokenProviderReference;

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

    @Column(name = "mpesa_receipt_number", length = 30)
    private String mpesaReceiptNumber;

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

    public void markTokenIssued(String token, BigDecimal units, String providerReference) {
        this.token = token;
        this.tokenUnits = units;
        this.tokenProviderReference = providerReference;
        this.status = Status.TOKEN_ISSUED;
    }

    // Retries are used up or the token can never be issued. needsReview puts the row
    // on the logical dead letter list so someone can vend by hand or refund.
    public void markTokenIssuanceFailed(String reason) {
        this.status = Status.FAILED_TOKEN_ISSUANCE;
        this.failureReason = reason != null && reason.length() > 255 ? reason.substring(0, 255) : reason;
        this.needsReview = true;
    }

    public void setToken(String token, BigDecimal units, String s) {
    }
}
