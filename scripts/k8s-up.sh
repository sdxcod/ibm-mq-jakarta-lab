#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
kubectl apply -f k8s/namespace.yaml
if ! kubectl -n ibm-mq-lab get secret mq-lab-passwords >/dev/null 2>&1; then
  ./scripts/init-lab.sh
  kubectl -n ibm-mq-lab create secret generic mq-lab-passwords \
    --from-file=mqAppPassword=.secrets/mqAppPassword \
    --from-file=mqAdminPassword=.secrets/mqAdminPassword
fi
kubectl apply -k k8s
kubectl -n ibm-mq-lab rollout status statefulset/mq --timeout=600s
printf 'MQ ready. Existing Kubernetes password secret preserved. Apply the demo Job after loading the agent image.\n'
