package io.github.darlene.utilitypaymentplatform.callback.infrastructure;

import io.github.darlene.utilitypaymentplatform.callback.CallBacklog;
import org.hibernate.validator.constraints.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface CallBacklogRepository extends JpaRepository <CallBacklog, UUID> {

    // For dedupes to the database.
    // Takes the .save method from jpa repository
    //We will attempt to save once and catch the error instead of finding if they exist first making two db round trips, increasing latency.



}
