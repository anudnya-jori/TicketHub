package com.tickethub.payment.reconciliation;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Internal (service key): run reconciliation now and get the report; also runs on a schedule. */
@RestController
@RequestMapping("/internal/reconciliation")
public class ReconciliationController {

    private final ReconciliationService service;

    private final boolean enabled;

    public ReconciliationController(ReconciliationService service,
                                    @Value("${payment.reconciliation.enabled:true}") boolean enabled) {
        this.service = service;
        this.enabled = enabled;
    }

    @PostMapping("/run")
    public ReconciliationService.Report run() {
        return service.reconcile();
    }

    @Scheduled(fixedDelayString = "${payment.reconciliation.interval-ms:60000}", initialDelayString = "${payment.reconciliation.initial-delay-ms:30000}")
    public void scheduled() {
        if (enabled) {
            service.reconcile();
        }
    }
}
