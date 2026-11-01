package com.tickethub.gateway.error;

import java.time.Instant;
import java.util.List;

/** Same JSON shape as every TicketHub service (ST-50), so clients see ONE error format. */
public record GatewayErrorResponse(Instant timestamp, int status, String error, String code, String message,
                                   String path, List<Object> details) {
}
