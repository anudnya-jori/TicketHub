package com.tickethub.payment.provider;

/** Dev-only knobs to force an outcome or a delay (to demo failure and timeout). Null = use defaults. */
public record SimulationHints(String forcedOutcome, Long delayMs) {
    public static SimulationHints none() { return new SimulationHints(null, null); }
}
