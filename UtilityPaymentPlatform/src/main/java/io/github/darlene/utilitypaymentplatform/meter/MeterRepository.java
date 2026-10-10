package io.github.darlene.utilitypaymentplatform.meter;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional; //Comes with .orElseThrow
import java.util.UUID;

@Repository
public interface MeterRepository extends JpaRepository<Meter, UUID> {

    // Load the customer in the same query: Meter gets cached as JSON in Redis,
    // and an uninitialized lazy proxy can't be serialized
    @EntityGraph(attributePaths = "customer")
    Optional<Meter> findByMeterNumber(String meterNumber);

    boolean existsByMeterNumber(String meterNumber); //Useful for a fast fail validation check
}