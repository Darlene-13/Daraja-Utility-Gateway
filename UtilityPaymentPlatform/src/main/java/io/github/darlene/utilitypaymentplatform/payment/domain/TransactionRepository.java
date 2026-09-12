package io.github.darlene.utilitypaymentplatform.payment.domain;


import org.hibernate.validator.constraints.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface TransactionRepository extends JpaRepository <Transaction, Long> {

    Transaction findById(UUID id);

    Transaction findByCheckoutRequestId(String checkOutRequestId);  // Needed by checkOutRequestId

}
