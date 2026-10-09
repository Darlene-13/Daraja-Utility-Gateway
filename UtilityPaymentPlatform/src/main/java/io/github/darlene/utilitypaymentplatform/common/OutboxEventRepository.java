package io.github.darlene.utilitypaymentplatform.common;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OutboxEventRepository extends JpaRepository <OutboxEvent, UUID> {

}
