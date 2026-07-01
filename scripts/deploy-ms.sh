#!/usr/bin/env bash
# Blue-green deploy for andromeda-ms.
# Required env vars: IMAGE, ENV_FILE
set -euo pipefail

BLUE="andromeda-ms-blue"
GREEN="andromeda-ms-green"
GREEN_PORT=9091
BLUE_PORT=9090
HEALTH_URL="http://localhost:${GREEN_PORT}/actuator/health"
HEALTH_RETRIES=12
HEALTH_INTERVAL=5

echo "[deploy-ms] Pulling ${IMAGE}"
docker pull "${IMAGE}"

echo "[deploy-ms] Starting green container on port ${GREEN_PORT}"
docker rm -f "${GREEN}" 2>/dev/null || true
docker run -d \
  --name "${GREEN}" \
  --env-file "${ENV_FILE}" \
  --network andromeda-net \
  -p "127.0.0.1:${GREEN_PORT}:9090" \
  --restart unless-stopped \
  "${IMAGE}"

echo "[deploy-ms] Waiting for health check at ${HEALTH_URL}"
for i in $(seq 1 ${HEALTH_RETRIES}); do
  if curl -sf "${HEALTH_URL}" | grep -q '"status":"UP"'; then
    echo "[deploy-ms] Green is healthy"
    break
  fi
  if [ "${i}" -eq "${HEALTH_RETRIES}" ]; then
    echo "[deploy-ms] Health check failed — rolling back"
    docker rm -f "${GREEN}"
    exit 1
  fi
  echo "[deploy-ms] Attempt ${i}/${HEALTH_RETRIES} — retrying in ${HEALTH_INTERVAL}s"
  sleep "${HEALTH_INTERVAL}"
done

echo "[deploy-ms] Switching traffic: stopping blue, promoting green"
docker rm -f "${BLUE}" 2>/dev/null || true
docker stop "${GREEN}"
docker rm "${GREEN}"

docker run -d \
  --name "${BLUE}" \
  --env-file "${ENV_FILE}" \
  --network andromeda-net \
  -p "127.0.0.1:${BLUE_PORT}:9090" \
  --restart unless-stopped \
  "${IMAGE}"

echo "[deploy-ms] Deploy complete — ${IMAGE} running as ${BLUE} on port ${BLUE_PORT}"
