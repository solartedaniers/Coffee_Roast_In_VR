package com.toastedvr.toastedvr.backend.config;

import com.toastedvr.toastedvr.backend.service.RoastEvaluationProcessor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

// Revisa cada cierto tiempo si hay calificaciones pendientes. Los tests lo apagan y llaman al
// procesador directamente.
@Configuration
@EnableScheduling
@ConditionalOnProperty(prefix = "app.roast-evaluation", name = "scheduler-enabled", havingValue = "true", matchIfMissing = true)
public class RoastEvaluationScheduler {

    private final RoastEvaluationProcessor processor;

    public RoastEvaluationScheduler(RoastEvaluationProcessor processor) {
        this.processor = processor;
    }

    @Scheduled(
        initialDelayString = "${app.roast-evaluation.poll-millis:5000}",
        fixedDelayString = "${app.roast-evaluation.poll-millis:5000}"
    )
    public void processPendingEvaluations() {
        processor.processDue();
    }
}
