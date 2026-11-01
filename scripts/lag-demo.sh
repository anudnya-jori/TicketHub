#!/usr/bin/env bash
# Scenario 11: consumer lag. Slow the consumer, publish a burst, watch lag, then scale out.
#   NOTIFICATION_DELAY_MS=2000 mvn -pl notification-service spring-boot:run     (2 s per message)
#   then run this script. Start a 2nd/3rd instance with  SERVER_PORT=8094 / 8095  (same group) and watch lag drain faster.
#   NOTE: the topic has 3 partitions, so a 4th consumer in the group stays idle.
set -euo pipefail
N=${1:-500}
KAFKA="docker exec -i tickethub-kafka /opt/kafka/bin"
for i in $(seq 1 "$N"); do
  id=$((900000+i))
  echo "$id:{\"eventId\":\"lag-$id\",\"bookingId\":$id,\"customerId\":\"c\",\"recipient\":\"lag$id@test.dev\",\"showId\":1,\"seatIds\":[1],\"amount\":10.00,\"currency\":\"INR\",\"confirmedAt\":\"2030-01-01T10:00:00Z\"}"
done | $KAFKA/kafka-console-producer.sh --bootstrap-server localhost:9092 --topic booking.confirmed \
       --property parse.key=true --property key.separator=:
echo "published $N events. Watching lag (Ctrl-C to stop); Kafka UI: http://localhost:8090, Grafana panel 'Kafka consumer lag'"
while true; do
  $KAFKA/kafka-consumer-groups.sh --bootstrap-server localhost:9092 --describe --group notification-service 2>/dev/null | awk 'NR==1||/booking.confirmed/'
  sleep 5
done
