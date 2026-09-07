#!/usr/bin/env bash
# Idempotent Cloud Agent setup for SmartLab 2.0.
# Prepares backend (Java 21 / Maven / Spring Boot) and frontend (Node / Vite / Vue 3)
# dependencies and a local MQTT broker. The backend reads its PostgreSQL and MQTT
# configuration from Backend/src/main/resources/application.yml.
set -euo pipefail

cd "$(dirname "$0")/.."

# System packages that are not part of the base image (Java 21 and Node are preinstalled).
if ! command -v mvn >/dev/null 2>&1 || ! command -v mosquitto >/dev/null 2>&1; then
  sudo apt-get update
  sudo DEBIAN_FRONTEND=noninteractive apt-get install -y \
    maven mosquitto mosquitto-clients postgresql-client
fi

# Backend: resolve dependencies and produce the runnable artifact.
( cd Backend && mvn -q -DskipTests package )

# Frontend: install locked dependencies.
( cd Frontend && npm ci )

echo "SmartLab environment setup complete."
