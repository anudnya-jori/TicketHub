#!/usr/bin/env bash
# Creates the Kibana "data view" for TicketHub logs once Kibana is up (http://localhost:5601).
until curl -s http://localhost:5601/api/status | grep -q '"level":"available"'; do echo "waiting for Kibana..."; sleep 5; done
curl -s -X POST http://localhost:5601/api/data_views/data_view -H 'kbn-xsrf: true' -H 'Content-Type: application/json' \
  -d '{"data_view":{"title":"tickethub-logs-*","name":"TicketHub logs","timeFieldName":"@timestamp"},"override":true}' >/dev/null \
  && echo "Done. Open http://localhost:5601/app/discover and search e.g.  bookingId:1   or   correlationId:\"<id>\""
