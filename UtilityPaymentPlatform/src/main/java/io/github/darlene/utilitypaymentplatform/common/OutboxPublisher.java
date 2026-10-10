package io.github.darlene.utilitypaymentplatform.common;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

// Moves outbox rows onto Redis streams. The processors only write to the DB, in the same
// transaction as their business change, and this is the single place that talks to Redis.
@Slf4j
@Component
public class OutboxPublisher {

    private final OutboxEventRepository outboxEventRepository;
    private final StringRedisTemplate redisTemplate;
    private final int batchSize;

    // event type -> stream. Each consumer only gets the events it acts on.
    private final Map<String, String> routes;

    public OutboxPublisher(OutboxEventRepository outboxEventRepository,
                           StringRedisTemplate redisTemplate,
                           @Value("${outbox.batch-size}") int batchSize,
                           @Value("${token.stream-name}") String paymentStream,
                           @Value("${notification.stream-name}") String notificationStream) {
        this.outboxEventRepository = outboxEventRepository;
        this.redisTemplate = redisTemplate;
        this.batchSize = batchSize;
        this.routes = Map.of(
                "PAYMENT_CONFIRMED", paymentStream,          // token worker vends
                "PAYMENT_FAILED", notificationStream,        // tell the customer the payment failed
                "TOKEN_ISSUED", notificationStream,          // send the token by SMS
                "TOKEN_ISSUANCE_FAILED", notificationStream  // tell the customer, ops reviews
        );
    }

    // Rows stay PENDING until the XADD succeeds, so if Redis is down nothing is lost:
    // the next poll picks them up again. That is the retry for this hop.
    @Scheduled(fixedDelayString = "${outbox.poll-interval-ms}")
    @Transactional
    public void publishPending() {
        List<OutboxEvent> batch = outboxEventRepository.lockPendingBatch(batchSize);

        for (OutboxEvent event : batch) {
            String stream = routes.get(event.getEventType());
            if (stream == null) {
                // retrying will not make a route appear, so take it out of the queue
                log.error("No stream for event type {}, marking outbox event {} FAILED",
                        event.getEventType(), event.getId());
                event.setStatus(OutboxStatus.FAILED);
                continue;
            }

            try {
                redisTemplate.opsForStream().add(stream, Map.of(
                        "outboxId", event.getId().toString(),
                        "eventType", event.getEventType(),
                        "aggregateId", event.getAggregateId().toString(),
                        "payload", event.getPayload()));
            } catch (Exception e) {
                // Redis is most likely down, so the rest of the batch would fail too. Stop here
                // and let the commit keep the PUBLISHED marks for the events that did go out.
                log.error("Publishing outbox event {} failed, retrying next poll", event.getId(), e);
                break;
            }

            event.setStatus(OutboxStatus.PUBLISHED);
            event.setPublishedAt(OffsetDateTime.now());
        }
        // No explicit save: the rows are managed entities, so the changes flush on commit.
        // If we crash after an XADD but before the commit, that event goes out again on the
        // next poll. Consumers must be idempotent, which TokenProcessor already is.
    }
}
