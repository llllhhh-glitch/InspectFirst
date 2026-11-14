#!/bin/bash
set -euo pipefail
project_dir="$(cd "$(dirname "$0")/.." && pwd)"
config_file="$project_dir/.local/guide-db.properties"
if [[ ! -f "$config_file" ]]; then
  echo 'Local MySQL configuration is missing. Complete database recovery/setup first.'
  read -r -p 'Press Enter to close. '
  exit 1
fi
export SPRING_CONFIG_ADDITIONAL_LOCATION="file:$config_file"
cd "$project_dir/backend/guide-service"
mvn spring-boot:run
