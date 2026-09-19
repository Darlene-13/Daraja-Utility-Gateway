package io.github.darlene.utilitypaymentplatform.payment.domain;


import org.hibernate.validator.constraints.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface TransactionRepository extends JpaRepository <Transaction, Long> {

    Optional<Transaction> findById(UUID id);

    Transaction findByCheckoutRequestId(String checkOutRequestId);  // Needed by checkOutRequestId

    boolean existsByPhoneNumberAndStatus(String phoneNumber, Status PENDING_CONFIRMATION);

}
