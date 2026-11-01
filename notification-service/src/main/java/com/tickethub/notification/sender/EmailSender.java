package com.tickethub.notification.sender;

/** Delivery channel. Throws if sending fails. */
public interface EmailSender {
    void send(String to, String subject, String body);
}
