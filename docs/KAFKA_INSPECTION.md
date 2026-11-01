# Scenario 0 - seeing inside Kafka
`docker compose up -d` then open **Kafka UI: http://localhost:8090** (cluster `tickethub`): Topics -> `booking.confirmed`
(partitions, offsets, messages); Consumers -> `notification-service` (lag per partition).

CLI inside the container:
```bash
docker exec -it tickethub-kafka /opt/kafka/bin/kafka-topics.sh --bootstrap-server localhost:9092 --list
docker exec -it tickethub-kafka /opt/kafka/bin/kafka-consumer-groups.sh --bootstrap-server localhost:9092 --describe --group notification-service
# publish a test message (key:value)
docker exec -it tickethub-kafka /opt/kafka/bin/kafka-console-producer.sh --bootstrap-server localhost:9092 --topic booking.confirmed --property parse.key=true --property key.separator=:
#   1:{"eventId":"x1","bookingId":1}        <- no recipient: goes to booking.confirmed.DLT
docker exec -it tickethub-kafka /opt/kafka/bin/kafka-console-consumer.sh --bootstrap-server localhost:9092 --topic booking.confirmed.DLT --from-beginning
```
Inspect / replay dead letters (service key needed in the secure profile: header `X-Internal-Api-Key`):
```bash
curl localhost:8084/internal/dead-letters
curl -X POST localhost:8084/internal/dead-letters/<partition>/<offset>/replay
```
Terms: partition = ordered log shard (key bookingId picks it); offset = position in a partition; consumer group = consumers sharing a topic's work; lag = latest offset - committed offset.
