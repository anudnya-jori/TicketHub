package com.tickethub.notification.sender;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/** Default: "sends" the email by writing it to the log. No SMTP server needed. */
@Component
@ConditionalOnProperty(name = "notification.sender", havingValue = "logging", matchIfMissing = true)
public class LoggingEmailSender implements EmailSender {
    private static final Logger log = LoggerFactory.getLogger(LoggingEmailSender.class);

    @Override
    public void send(String to, String subject, String body) {
        log.info("EMAIL to={} subject=\"{}\"\n{}", to, subject, body);
    }
}
