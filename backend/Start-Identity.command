#!/bin/bash
set -euo pipefail
project_dir="$(cd "$(dirname "$0")/.." && pwd)"
config_file="$project_dir/.local/identity-db.properties"
if [[ ! -f "$config_file" ]]; then
  echo 'Run database/Setup-Accounts.command first. Do not run MySQL recovery again.'
  read -r -p 'Press Enter to close. '
  exit 1
fi
export SPRING_CONFIG_ADDITIONAL_LOCATION="file:$config_file"
cd "$project_dir/backend/identity-service"
if [[ -f target/identity-service-0.1.0.jar ]]; then
  java -jar target/identity-service-0.1.0.jar
else
  mvn spring-boot:run
fi
