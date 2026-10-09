package io.github.darlene.utilitypaymentplatform.payment.domain;


import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;


@Repository
public interface TransactionRepository extends JpaRepository<Transaction, UUID> {

    Transaction findByCheckoutRequestId(String checkOutRequestId);  // Needed by checkOutRequestId

    boolean existsByPhoneNumberAndStatus(String phoneNumber, Status PENDING_CONFIRMATION);

}
