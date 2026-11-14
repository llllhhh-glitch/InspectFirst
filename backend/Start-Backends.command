#!/bin/bash
set -euo pipefail
backend_dir="$(cd "$(dirname "$0")" && pwd)"
project_dir="$(dirname "$backend_dir")"
for port in 8086 8087 8088 8089 19092 19093; do
  if lsof -nP -iTCP:"$port" -sTCP:LISTEN >/dev/null 2>&1; then
    echo "Port $port is already in use. Stop the existing project service before starting this launcher."
    read -r -p 'Press Enter to close. '
    exit 1
  fi
done
for service in guide identity inspection report; do
  [[ -f "$project_dir/.local/$service-db.properties" ]] || { echo 'Complete database/Upgrade-Backend.command first.'; exit 1; }
  [[ -f "$backend_dir/$service-service/target/$service-service-0.1.0.jar" ]] || { echo 'Build backend/pom.xml first: mvn test package'; exit 1; }
done
mkdir -p "$project_dir/.local/logs"
pids=()
cleanup() { for pid in "${pids[@]}"; do kill "$pid" 2>/dev/null || true; done; }
trap cleanup EXIT INT TERM
"$backend_dir/Start-Kafka.command" > "$project_dir/.local/logs/kafka.log" 2>&1 &
pids+=("$!")
for service in guide identity inspection report; do
  (
    cd "$backend_dir/$service-service"
    export SPRING_CONFIG_ADDITIONAL_LOCATION="file:$project_dir/.local/$service-db.properties"
    exec java -Xmx256m -jar "target/$service-service-0.1.0.jar"
  ) > "$project_dir/.local/logs/$service.log" 2>&1 &
  pids+=("$!")
done
echo 'Starting four backend services and project-local Kafka. Logs are in InspectFirst/.local/logs.'
echo 'Use http://127.0.0.1:5173 after starting the frontend. Press Ctrl+C to stop these services.'
wait
