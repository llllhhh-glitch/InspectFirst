#!/bin/bash
set -euo pipefail
project_dir="$(cd "$(dirname "$0")/.." && pwd)"
kafka_dir="${KAFKA_HOME:-/Users/leonluo/Documents/Codex/kafka}"
config="$project_dir/.local/kafka-server.properties"
data_dir="$project_dir/.local/kafka-data"
mkdir -p "$project_dir/.local"
if [[ ! -x "$kafka_dir/bin/kafka-server-start.sh" ]]; then
  echo 'Set KAFKA_HOME to an installed Apache Kafka directory.'
  exit 1
fi
if [[ ! -f "$config" ]]; then
  cat > "$config" <<CONFIG
process.roles=broker,controller
node.id=1
controller.quorum.voters=1@127.0.0.1:19093
listeners=PLAINTEXT://127.0.0.1:19092,CONTROLLER://127.0.0.1:19093
advertised.listeners=PLAINTEXT://127.0.0.1:19092
listener.security.protocol.map=CONTROLLER:PLAINTEXT,PLAINTEXT:PLAINTEXT
controller.listener.names=CONTROLLER
inter.broker.listener.name=PLAINTEXT
log.dirs=$data_dir
message.max.bytes=8388608
replica.fetch.max.bytes=10485760
num.partitions=1
offsets.topic.replication.factor=1
transaction.state.log.replication.factor=1
transaction.state.log.min.isr=1
group.initial.rebalance.delay.ms=0
CONFIG
fi
if ! grep -q "^message.max.bytes=" "$config"; then
  printf "\nmessage.max.bytes=8388608\nreplica.fetch.max.bytes=10485760\n" >> "$config"
fi
if [[ ! -f "$data_dir/meta.properties" ]]; then
  mkdir -p "$data_dir"
  if [[ -n "$(find "$data_dir" -mindepth 1 -maxdepth 1 -print -quit)" ]]; then
    echo 'Kafka data directory is nonempty without metadata. Refusing to format it.'
    exit 1
  fi
  cluster_id="$("$kafka_dir/bin/kafka-storage.sh" random-uuid)"
  "$kafka_dir/bin/kafka-storage.sh" format -t "$cluster_id" -c "$config"
fi
export KAFKA_HEAP_OPTS="${KAFKA_HEAP_OPTS:--Xmx256m -Xms128m}"
exec "$kafka_dir/bin/kafka-server-start.sh" "$config"
