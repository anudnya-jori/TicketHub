package com.tickethub.notification.dlt;

/** A message parked on the dead-letter topic, with the reason the consumer gave up on it. */
public record DeadLetterRecord(int partition, long offset, String key, String value, String error,
                               String originalTopic) {
}
