package io.github.darlene.utilitypaymentplatform.common;

import io.github.darlene.utilitypaymentplatform.payment.domain.TransactionRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

// Anything parked in a dead-letter stream or flagged needsReview has left the retry loops
// and only a person can fix it. This makes sure that person hears about it: an ERROR log
// each time one of them grows, which is what log alerting should be pointed at.
@Slf4j
@Component
public class DeadLetterMonitor {

    private static final String NEEDS_REVIEW = "transactions needing review";

    private final StringRedisTemplate redisTemplate;
    private final TransactionRepository transactionRepository;
    private final List<String> deadLetterStreams;

    // last count seen per source, so we alert on new arrivals instead of every run
    private final Map<String, Long> lastSeen = new HashMap<>();

    public DeadLetterMonitor(StringRedisTemplate redisTemplate,
                             TransactionRepository transactionRepository,
                             @Value("${callback.dead-letter-stream}") String callbackDeadLetters,
                             @Value("${token.dead-letter-stream}") String tokenDeadLetters) {
        this.redisTemplate = redisTemplate;
        this.transactionRepository = transactionRepository;
        this.deadLetterStreams = List.of(callbackDeadLetters, tokenDeadLetters);
    }

    @Scheduled(fixedDelayString = "${dead-letter-monitor.interval-ms}")
    public void check() {
        for (String stream : deadLetterStreams) {
            try {
                // XLEN is 0 for a stream that doesn't exist yet
                Long size = redisTemplate.opsForStream().size(stream);
                report(stream, size == null ? 0 : size);
            } catch (Exception e) {
                log.warn("Could not read dead-letter stream {}", stream, e);
            }
        }

        try {
            report(NEEDS_REVIEW, transactionRepository.countByNeedsReviewTrue());
        } catch (Exception e) {
            log.warn("Could not count transactions needing review", e);
        }
    }

    private void report(String source, long count) {
        Long previous = lastSeen.put(source, count);
        if (previous == null) {
            // first check since startup: say what is already waiting, once
            if (count > 0) {
                log.warn("{} has {} item(s) waiting for a person", source, count);
            }
        } else if (count > previous) {
            log.error("{} new item(s) in {}, {} in total waiting for a person",
                    count - previous, source, count);
        }
    }
}
