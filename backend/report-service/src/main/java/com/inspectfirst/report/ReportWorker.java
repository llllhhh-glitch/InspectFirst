package com.inspectfirst.report;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
@Component
@ConditionalOnProperty(name="inspectfirst.reports-enabled",havingValue="true",matchIfMissing=true)
public class ReportWorker {
    private final ReportRenderer renderer;
    public ReportWorker(ReportRenderer renderer) { this.renderer=renderer; }
    @Scheduled(fixedDelay=1500) public void run() { renderer.next(); }
}
