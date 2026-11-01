package com.tickethub.notification.controller;

import com.tickethub.notification.dto.StatusResponse;
import com.tickethub.notification.service.StatusService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/** Shows the controller -> service layering. Real health checks live at /actuator/health. */
@RestController
public class StatusController {

    private final StatusService statusService;

    public StatusController(StatusService statusService) {
        this.statusService = statusService;
    }

    @GetMapping("/status")
    public StatusResponse status() {
        return statusService.status();
    }
}
