package com.tickethub.notification.dlt;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/** Internal only (service key required in the secure profile): inspect and replay dead-lettered messages. */
@RestController
@RequestMapping("/internal/dead-letters")
public class DeadLetterController {

    private final DeadLetterService service;

    public DeadLetterController(DeadLetterService service) {
        this.service = service;
    }

    @GetMapping
    public List<DeadLetterRecord> list(@RequestParam(defaultValue = "50") int max) {
        return service.list(Math.min(Math.max(max, 1), 500));
    }

    /** Fix the cause first (code or data), then replay: the message goes back to the main topic. */
    @PostMapping("/{partition}/{offset}/replay")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public DeadLetterRecord replay(@PathVariable int partition, @PathVariable long offset) {
        return service.replay(partition, offset);
    }
}
