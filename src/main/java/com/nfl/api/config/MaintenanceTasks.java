package com.nfl.api.config;

import com.nfl.api.repository.IdempotencyRecordRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

import java.time.Instant;

/** Rotinas de manutenção: remove do banco as chaves de idempotência já expiradas. */
@Configuration
@EnableScheduling
public class MaintenanceTasks {

    private static final Logger log = LoggerFactory.getLogger(MaintenanceTasks.class);

    private final IdempotencyRecordRepository idempotencyRecords;

    public MaintenanceTasks(IdempotencyRecordRepository idempotencyRecords) {
        this.idempotencyRecords = idempotencyRecords;
    }

    @Scheduled(initialDelay = 10 * 60 * 1000, fixedDelay = 60 * 60 * 1000)
    public void purgeExpiredIdempotencyRecords() {
        int removed = idempotencyRecords.deleteExpired(Instant.now());
        if (removed > 0) {
            log.info("Chaves de idempotência expiradas removidas: {}", removed);
        }
    }
}
