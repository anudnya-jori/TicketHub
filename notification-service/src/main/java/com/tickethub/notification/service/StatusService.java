package com.tickethub.notification.service;

import com.tickethub.notification.dto.StatusResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class StatusService {

    private final String serviceName;

    public StatusService(@Value("${spring.application.name}") String serviceName) {
        this.serviceName = serviceName;
    }

    public StatusResponse status() {
        return new StatusResponse(serviceName, "UP", "0.1.0");
    }
}
