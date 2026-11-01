package __PKG__.exception;

import java.time.Instant;
import java.util.List;

/**
 * The ONE error format used by every TicketHub service (backlog ST-50).
 * Never contains stack traces.
 */
public record ErrorResponse(
        Instant timestamp,
        int status,
        String error,
        String code,
        String message,
        String path,
        List<FieldDetail> details) {

    /** One field-level validation problem. */
    public record FieldDetail(String field, String message) {
    }
}
