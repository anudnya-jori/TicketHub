package com.tickethub.booking.service;

import com.tickethub.booking.dto.StatusResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class StatusService {

    private final String serviceName;
    private final long reservationMinutes;

    public StatusService(@Value("${spring.application.name}") String serviceName,
                         @Value("${booking.reservation-minutes}") long reservationMinutes) {
        this.serviceName = serviceName;
        this.reservationMinutes = reservationMinutes;
    }

    public StatusResponse status() {
        return new StatusResponse(serviceName, "UP", "0.1.0", reservationMinutes);
    }
}
