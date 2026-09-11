package io.github.darlene.utilitypaymentplatform.meter;


import lombok.AllArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.data.redis.core.RedisTemplate;

import java.time.Duration;

@Service
@AllArgsConstructor
public class MeterValidationService {

    private static final String CACHE_PREFIX = "meter";
    private static final Duration CACHE_TTL= Duration.ofMinutes(10);
    private final MeterRepository meterRepository;
    private final CustomerRepository customerRepository;

    //Inject redis template
    private final RedisTemplate<String, Meter> redisTemplate;

    //EntryPoint
    public Meter validate(String meterNumber){
        Meter cached = checkCache(meterNumber);

        if (cached != null){
            return cached;
        }

        Meter meter = meterRepository.findByMeterNumber(meterNumber)
                .orElseThrow(() -> new MeterNumberNotFoundException(meterNumber));

        //Cache it
        encache(meterNumber, meter);
        return meter;
    }


    //checkCache for meter number
    //opsForValue: Treat Redis as a simple KV store.
    public Meter checkCache(String meterNumber){
        return redisTemplate.opsForValue().get(CACHE_PREFIX + ":" + meterNumber);
    }

    private void encache(String meterNumber, Meter meter){
        redisTemplate.opsForValue().set(CACHE_PREFIX + ":" + meterNumber, meter, CACHE_TTL);
    }

    private void decache(String meterNumber, Meter meter){
        redisTemplate.delete(CACHE_PREFIX + ":"+ meterNumber);
    }

}