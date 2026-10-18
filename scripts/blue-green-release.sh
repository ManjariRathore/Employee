#!/usr/bin/env bash

set -euo pipefail

CLUSTER_NAME="${CLUSTER_NAME:-employee-ms}"
NAMESPACE="${NAMESPACE:-employee-ms}"
PREVIEW_PORT="${PREVIEW_PORT:-18080}"
CONTEXT="kind-${CLUSTER_NAME}"
REPOSITORY_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

usage() {
  cat <<'EOF'
Usage:
  ./scripts/blue-green-release.sh deploy <image>
  ./scripts/blue-green-release.sh rollback
  ./scripts/blue-green-release.sh status

Optional environment variables:
  SOURCE_REVISION  Source commit represented by the image tag
  CLUSTER_NAME     kind cluster name (default: employee-ms)
  NAMESPACE        Kubernetes namespace (default: employee-ms)
  PREVIEW_PORT     Local smoke-test port (default: 18080)
EOF
}

for command in kubectl curl jq; do
  if ! command -v "${command}" >/dev/null 2>&1; then
    echo "Required command not found: ${command}" >&2
    exit 1
  fi
done

active_slot() {
  kubectl --context "${CONTEXT}" --namespace "${NAMESPACE}" get service employee-api \
    --output='jsonpath={.spec.selector.app\.kubernetes\.io/slot}'
}

deployment_for_slot() {
  if [[ "$1" == "blue" ]]; then
    echo "employee-api"
  else
    echo "employee-api-green"
  fi
}

inactive_slot() {
  if [[ "$1" == "blue" ]]; then
    echo "green"
  else
    echo "blue"
  fi
}

switch_service() {
  local slot="$1"

  kubectl --context "${CONTEXT}" --namespace "${NAMESPACE}" patch service employee-api \
    --type=merge \
    --patch "{\"spec\":{\"selector\":{\"app.kubernetes.io/name\":\"employee-api\",\"app.kubernetes.io/slot\":\"${slot}\"}}}"
}

show_status() {
  local slot
  slot="$(active_slot)"

  echo "Active slot: ${slot:-not configured}"
  kubectl --context "${CONTEXT}" --namespace "${NAMESPACE}" get deployments \
    --selector='app.kubernetes.io/name=employee-api' \
    --output=custom-columns='NAME:.metadata.name,READY:.status.readyReplicas,IMAGE:.spec.template.spec.containers[0].image,COMMIT:.spec.template.spec.containers[0].env[?(@.name=="GIT_COMMIT")].value'
}

command="${1:-}"

case "${command}" in
  status)
    show_status
    ;;

  rollback)
    current="$(active_slot)"
    if [[ "${current}" != "blue" && "${current}" != "green" ]]; then
      echo "The Service does not have a valid blue-green slot selector" >&2
      exit 1
    fi

    target="$(inactive_slot "${current}")"
    target_deployment="$(deployment_for_slot "${target}")"

    kubectl --context "${CONTEXT}" --namespace "${NAMESPACE}" get \
      deployment "${target_deployment}" >/dev/null
    kubectl --context "${CONTEXT}" --namespace "${NAMESPACE}" rollout status \
      deployment/"${target_deployment}" --timeout=5m

    switch_service "${target}"
    echo "Rolled back traffic from ${current} to ${target}"
    show_status
    ;;

  deploy)
    image="${2:-}"
    if [[ -z "${image}" ]]; then
      usage
      exit 1
    fi

    if [[ "${image}" == "latest" || "${image}" == *:latest ]]; then
      echo "Use an immutable image tag or digest, not latest" >&2
      exit 1
    fi

    current="$(active_slot)"
    if [[ "${current}" != "blue" && "${current}" != "green" ]]; then
      echo "Deploy the normal local stack first so the blue slot exists" >&2
      exit 1
    fi

    target="$(inactive_slot "${current}")"
    target_deployment="$(deployment_for_slot "${target}")"
    source_revision="${SOURCE_REVISION:-${image##*:}}"

    if command -v docker >/dev/null 2>&1 \
      && command -v kind >/dev/null 2>&1 \
      && docker image inspect "${image}" >/dev/null 2>&1; then
      echo "Loading local image ${image} into ${CLUSTER_NAME}"
      kind load docker-image "${image}" --name "${CLUSTER_NAME}"
    fi

    target_paused=false
    resume_target() {
      if [[ "${target_paused}" == "true" ]]; then
        kubectl --context "${CONTEXT}" --namespace "${NAMESPACE}" rollout resume \
          deployment/"${target_deployment}" >/dev/null 2>&1 || true
      fi
    }
    trap resume_target EXIT

    if [[ "${target}" == "green" ]]; then
      if kubectl --context "${CONTEXT}" --namespace "${NAMESPACE}" get \
        deployment "${target_deployment}" >/dev/null 2>&1; then
        kubectl --context "${CONTEXT}" --namespace "${NAMESPACE}" rollout pause \
          deployment/"${target_deployment}"
        target_paused=true
      fi

      echo "Reconciling the green deployment"
      kubectl --context "${CONTEXT}" apply \
        --kustomize "${REPOSITORY_ROOT}/k8s/releases/green"
    fi

    echo "Deploying ${image} to inactive ${target} slot"
    if [[ "${target_paused}" != "true" ]]; then
      kubectl --context "${CONTEXT}" --namespace "${NAMESPACE}" rollout pause \
        deployment/"${target_deployment}"
      target_paused=true
    fi

    release_update_succeeded=true
    kubectl --context "${CONTEXT}" --namespace "${NAMESPACE}" set image \
      deployment/"${target_deployment}" employee-api="${image}" \
      || release_update_succeeded=false
    if [[ "${release_update_succeeded}" == "true" ]]; then
      kubectl --context "${CONTEXT}" --namespace "${NAMESPACE}" set env \
        deployment/"${target_deployment}" \
        APP_VERSION="${image}" GIT_COMMIT="${source_revision}" \
        || release_update_succeeded=false
    fi

    kubectl --context "${CONTEXT}" --namespace "${NAMESPACE}" rollout resume \
      deployment/"${target_deployment}"
    target_paused=false
    trap - EXIT

    if [[ "${release_update_succeeded}" != "true" ]]; then
      echo "Failed to update the inactive ${target} deployment" >&2
      exit 1
    fi

    kubectl --context "${CONTEXT}" --namespace "${NAMESPACE}" annotate \
      deployment/"${target_deployment}" \
      devops.example.com/source-revision="${source_revision}" --overwrite

    kubectl --context "${CONTEXT}" --namespace "${NAMESPACE}" rollout status \
      deployment/"${target_deployment}" --timeout=5m

    echo "Smoke-testing ${target} before switching production traffic"
    kubectl --context "${CONTEXT}" --namespace "${NAMESPACE}" port-forward \
      deployment/"${target_deployment}" "${PREVIEW_PORT}:8080" \
      >"/tmp/employee-api-${target}-port-forward.log" 2>&1 &
    port_forward_pid=$!
    trap 'kill "${port_forward_pid}" 2>/dev/null || true' EXIT

    BASE_URL="http://localhost:${PREVIEW_PORT}" "${REPOSITORY_ROOT}/scripts/smoke-test.sh"
    kill "${port_forward_pid}" 2>/dev/null || true
    wait "${port_forward_pid}" 2>/dev/null || true
    trap - EXIT

    switch_service "${target}"
    echo "Release complete: traffic switched from ${current} to ${target}"
    echo "Rollback command: ./scripts/blue-green-release.sh rollback"
    show_status
    ;;

  *)
    usage
    exit 1
    ;;
esac
