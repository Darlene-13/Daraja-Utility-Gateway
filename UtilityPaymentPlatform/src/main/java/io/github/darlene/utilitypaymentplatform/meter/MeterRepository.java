package io.github.darlene.utilitypaymentplatform.meter;


import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional; //Comes with .orElseThrow

@Repository
public interface MeterRepository extends JpaRepository<Meter, Long> {

    Optional<Meter> findByMeterNumber(String meterNumber);

    Optional<Meter> existsByMeterNumber(String meterNumber); //Useful for a fast fail validation check
}