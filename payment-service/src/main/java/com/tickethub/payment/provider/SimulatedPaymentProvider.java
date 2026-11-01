
package com.tickethub.payment.provider;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;

import java.math.BigDecimal;

/**
 * A fake provider for learning/testing.
 * No real money, no real card data anywhere.
 */
@Component
@ConditionalOnProperty(
        name = "payment.provider",
        havingValue = "simulated",
        matchIfMissing = true
)
public class SimulatedPaymentProvider implements PaymentProvider {

    private final String defaultOutcome;
    private final boolean allowOverrides;

    public SimulatedPaymentProvider(
            @Value("${payment.simulation.default-outcome:SUCCESS}") String defaultOutcome,
            @Value("${payment.simulation.allow-overrides:false}") boolean allowOverrides) {

        this.defaultOutcome = defaultOutcome;
        this.allowOverrides = allowOverrides;
    }

    @Override
    public String name() {
        return "SIMULATED";
    }

    @Override
    public ChargeOutcome charge(
            String providerReference,
            BigDecimal amount,
            String currency,
            SimulationHints hints) {

        String outcome = defaultOutcome;

        if (allowOverrides && hints != null) {

            if (hints.delayMs() != null && hints.delayMs() > 0) {
                try {
                    Thread.sleep(Math.min(hints.delayMs(), 60_000));
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return ChargeOutcome.declined("INTERRUPTED");
                }
            }

            if (hints.forcedOutcome() != null) {
                outcome = hints.forcedOutcome();
            }
        }

        return "FAILED".equalsIgnoreCase(outcome)
                ? ChargeOutcome.declined("CARD_DECLINED")
                : ChargeOutcome.ok();
    }

    /**
     * Deterministic refund ID per payment reference.
     * Repeating the call can never create a second refund.
     */
    @Override
    public RefundOutcome refund(
            ProviderIds ids,
            BigDecimal amount,
            String currency) {

        return new RefundOutcome("rfnd_" + ids.reference());
    }

    /**
     * The simulation has no remote ledger.
     * It reports the configured default outcome.
     */
    @Override
    public String fetchStatus(ProviderIds ids) {
        return "FAILED".equalsIgnoreCase(defaultOutcome)
                ? "FAILED"
                : "SUCCESS";
    }
}
