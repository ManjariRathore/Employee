#!/usr/bin/env bash

set -euo pipefail

CLUSTER_NAME="${CLUSTER_NAME:-employee-ms}"
NAMESPACE="${NAMESPACE:-employee-ms}"
IMAGE="${IMAGE:-employee-management-service:local}"
SOURCE_REVISION="${SOURCE_REVISION:-${IMAGE##*:}}"
DB_PASSWORD="${DB_PASSWORD:?Set DB_PASSWORD before running this script}"
CONTEXT="kind-${CLUSTER_NAME}"
REPOSITORY_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

for command in docker kind kubectl rg; do
  if ! command -v "${command}" >/dev/null 2>&1; then
    echo "Required command not found: ${command}" >&2
    exit 1
  fi
done

if ! kind get clusters | rg --fixed-strings --line-regexp --quiet "${CLUSTER_NAME}"; then
  echo "Creating kind cluster ${CLUSTER_NAME}"
  kind create cluster \
    --name "${CLUSTER_NAME}" \
    --config "${REPOSITORY_ROOT}/infra/kind/cluster.yaml"
fi

echo "Loading ${IMAGE} into ${CLUSTER_NAME}"
kind load docker-image "${IMAGE}" --name "${CLUSTER_NAME}"

echo "Creating namespace and local database Secret"
kubectl --context "${CONTEXT}" apply \
  -f "${REPOSITORY_ROOT}/k8s/overlays/local/namespace.yaml"

kubectl --context "${CONTEXT}" --namespace "${NAMESPACE}" create secret generic employee-db \
  --from-literal="DB_PASSWORD=${DB_PASSWORD}" \
  --dry-run=client \
  --output=yaml \
  | kubectl --context "${CONTEXT}" apply --filename=-

deployment_paused=false
resume_deployment() {
  if [[ "${deployment_paused}" == "true" ]]; then
    kubectl --context "${CONTEXT}" --namespace "${NAMESPACE}" rollout resume \
      deployment/employee-api >/dev/null 2>&1 || true
  fi
}
trap resume_deployment EXIT

if kubectl --context "${CONTEXT}" --namespace "${NAMESPACE}" get \
  deployment employee-api >/dev/null 2>&1; then
  kubectl --context "${CONTEXT}" --namespace "${NAMESPACE}" rollout pause \
    deployment/employee-api
  deployment_paused=true
fi

echo "Applying the local Kustomize overlay"
kubectl --context "${CONTEXT}" apply \
  --kustomize "${REPOSITORY_ROOT}/k8s/overlays/local"

if [[ "${deployment_paused}" != "true" ]]; then
  kubectl --context "${CONTEXT}" --namespace "${NAMESPACE}" rollout pause \
    deployment/employee-api
  deployment_paused=true
fi

echo "Selecting application image ${IMAGE}"
kubectl --context "${CONTEXT}" --namespace "${NAMESPACE}" set image \
  deployment/employee-api employee-api="${IMAGE}"

echo "Recording source revision ${SOURCE_REVISION}"
kubectl --context "${CONTEXT}" --namespace "${NAMESPACE}" set env \
  deployment/employee-api APP_VERSION="${IMAGE}" GIT_COMMIT="${SOURCE_REVISION}"

kubectl --context "${CONTEXT}" --namespace "${NAMESPACE}" rollout resume \
  deployment/employee-api
deployment_paused=false
trap - EXIT

echo "Waiting for MySQL"
kubectl --context "${CONTEXT}" --namespace "${NAMESPACE}" rollout status \
  statefulset/mysql --timeout=5m

echo "Waiting for Jaeger"
kubectl --context "${CONTEXT}" --namespace "${NAMESPACE}" rollout status \
  deployment/jaeger --timeout=5m

echo "Waiting for Prometheus"
kubectl --context "${CONTEXT}" --namespace "${NAMESPACE}" rollout status \
  deployment/prometheus --timeout=5m

echo "Waiting for the employee API"
kubectl --context "${CONTEXT}" --namespace "${NAMESPACE}" rollout status \
  deployment/employee-api --timeout=5m

kubectl --context "${CONTEXT}" --namespace "${NAMESPACE}" get pods,services,persistentvolumeclaims

echo
echo "Deployment is ready:"
echo "Employee API: http://localhost:8080"
echo "Prometheus:   http://localhost:9090"
echo "Jaeger:      http://localhost:16686"
