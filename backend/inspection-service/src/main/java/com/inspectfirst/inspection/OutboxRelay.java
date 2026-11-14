package com.inspectfirst.inspection;

import com.inspectfirst.shared.Store;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.concurrent.TimeUnit;

@Component
@ConditionalOnProperty(name="inspectfirst.events-enabled",havingValue="true",matchIfMissing=true)
public class OutboxRelay {
    private final Store db;private final KafkaTemplate<String,String> kafka;
    public OutboxRelay(Store db,KafkaTemplate<String,String> kafka) { this.db=db;this.kafka=kafka; }
    @Scheduled(fixedDelayString="${inspectfirst.outbox-delay:3000}") public void publish() {
        for(var row:db.rows("SELECT * FROM outbox_event WHERE status='PENDING' AND available_at<=CURRENT_TIMESTAMP ORDER BY created_at,id LIMIT 20")) {
            try {
                kafka.send("inspectfirst.inspection.events.v1",row.get("aggregateId").toString(),row.get("payload").toString()).get(12,TimeUnit.SECONDS);
                db.update("UPDATE outbox_event SET status='SENT',published_at=CURRENT_TIMESTAMP WHERE id=?",row.get("id"));
            } catch(Exception e) {
                if(e instanceof InterruptedException) Thread.currentThread().interrupt();
                db.update("UPDATE outbox_event SET attempts=attempts+1,available_at=? WHERE id=?",Timestamp.from(Instant.now().plusSeconds(10)),row.get("id"));
                break;
            }
        }
    }
}
