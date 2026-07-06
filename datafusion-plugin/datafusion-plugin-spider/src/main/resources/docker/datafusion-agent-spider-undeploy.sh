#!/usr/bin/env bash
set -euo pipefail

NAMESPACE="${NAMESPACE:-datafusion}"
RELEASE_NAME="${RELEASE_NAME:-datafusion-agent-spider}"
COMPONENT_LABEL="${COMPONENT_LABEL:-agent-spider}"
KUBECTL="${KUBECTL:-kubectl}"
TIMEOUT="${TIMEOUT:-180s}"

KUBECTL_ARGS=()
if [[ -n "${KUBECONFIG:-}" ]]; then
  KUBECTL_ARGS+=(--kubeconfig "${KUBECONFIG}")
fi

run_kubectl() {
  "${KUBECTL}" "${KUBECTL_ARGS[@]}" "$@"
}

echo "Stop datafusion-agent-spider resources in namespace ${NAMESPACE} ..."

if ! command -v "${KUBECTL}" >/dev/null 2>&1; then
  echo "kubectl not found: ${KUBECTL}" >&2
  exit 1
fi

# 1) 先缩容，优雅退出运行容器
if run_kubectl -n "${NAMESPACE}" get deployment "${RELEASE_NAME}" >/dev/null 2>&1; then
  run_kubectl -n "${NAMESPACE}" scale deployment "${RELEASE_NAME}" --replicas=0
  run_kubectl -n "${NAMESPACE}" wait --for=delete \
    "pod" -l "app.kubernetes.io/name=datafusion,app.kubernetes.io/component=${COMPONENT_LABEL}" \
    --timeout="${TIMEOUT}" || true
else
  echo "deployment ${RELEASE_NAME} not found in ${NAMESPACE}, skip scaling step"
fi

# 2) 清理 spider 相关资源（不删除 PVC，避免误删共享盘）
run_kubectl -n "${NAMESPACE}" delete deployment "${RELEASE_NAME}" --ignore-not-found
run_kubectl -n "${NAMESPACE}" delete service "${RELEASE_NAME}" --ignore-not-found
run_kubectl -n "${NAMESPACE}" delete configmap "${RELEASE_NAME}-env" --ignore-not-found
run_kubectl -n "${NAMESPACE}" delete secret "${RELEASE_NAME}-secret" --ignore-not-found
run_kubectl -n "${NAMESPACE}" delete serviceaccount "${RELEASE_NAME}" --ignore-not-found
run_kubectl -n "${NAMESPACE}" delete role "${RELEASE_NAME}-role" --ignore-not-found
run_kubectl -n "${NAMESPACE}" delete rolebinding "${RELEASE_NAME}-role" --ignore-not-found

echo "Done. Shared volume (if any) is intentionally kept."
