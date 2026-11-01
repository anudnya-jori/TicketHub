package com.tickethub.booking.dto;

/** reservationMinutes is shown so you can SEE which configuration (local or Config Server) is active. */
public record StatusResponse(String service, String status, String version, long reservationMinutes) {
}
