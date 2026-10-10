package io.github.darlene.utilitypaymentplatform.payment.domain;


import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;


@Repository
public interface TransactionRepository extends JpaRepository<Transaction, UUID> {

   Optional<Transaction> findByCheckoutRequestId(String checkOutRequestId);  // Needed by checkOutRequestId

    boolean existsByPhoneNumberAndStatus(String phoneNumber, Status PENDING_CONFIRMATION);

    // Used by ReconciliationJob: transactions whose callback is overdue, oldest first.
    // Rows already flagged for review are left for a person.
    List<Transaction> findTop50ByStatusAndNeedsReviewFalseAndUpdatedAtBeforeOrderByUpdatedAtAsc(
            Status status, OffsetDateTime before);

    // The logical dead letter: rows a person has to look at
    long countByNeedsReviewTrue();

}
