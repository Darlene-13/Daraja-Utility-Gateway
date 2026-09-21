package io.github.darlene.utilitypaymentplatform.callback;


import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.hibernate.validator.constraints.UUID;

import java.time.OffsetDateTime;

@Entity
@Table(name = "callback_logs")
@NoArgsConstructor
@Getter
@Setter
public class CallBacklog {

    @Id
    @GeneratedValue
    private UUID id;

    @NotNull
    @Column(name = "checkout_request_id", nullable = false, length = 50)
    private String checkOutRequestId;


    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "raw_payload", nullable = false, columnDefinition = "jsonb")
    private String rawPayload;

    @Column(name = "received_at", nullable = false, updatable = false)
    private OffsetDateTime receivedAt;

    @PrePersist
    void onCreate(){
        this.receivedAt = OffsetDateTime.now();
    }


}
