#!/usr/bin/env bash
set -euo pipefail

BROWSER_AGENT_PID=""
DATAFUSION_AGENT_PID=""

term() {
  if [[ -n "${BROWSER_AGENT_PID}" ]]; then
    kill -TERM "${BROWSER_AGENT_PID}" 2>/dev/null || true
  fi
  if [[ -n "${DATAFUSION_AGENT_PID}" ]]; then
    kill -TERM "${DATAFUSION_AGENT_PID}" 2>/dev/null || true
  fi
  wait "${BROWSER_AGENT_PID}" "${DATAFUSION_AGENT_PID}" 2>/dev/null || true
}

trap term TERM INT

cd /opt/browser-agent

BROWSER_AGENT_PYTHON="${BROWSER_AGENT_PYTHON_BIN:-/opt/browser-agent/.venv/bin/python}"
BROWSER_AGENT_OTEL="${BROWSER_AGENT_OTEL_BIN:-/opt/browser-agent/.venv/bin/opentelemetry-instrument}"
if [[ ! -x "${BROWSER_AGENT_PYTHON}" ]]; then
  echo "browser-agent python not found: ${BROWSER_AGENT_PYTHON}" >&2
  exit 1
fi

if [[ -f /opt/browser-agent/src/main.py ]]; then
  BROWSER_AGENT_MAIN="/opt/browser-agent/src/main.py"
elif [[ -f /opt/browser-agent/main.py ]]; then
  BROWSER_AGENT_MAIN="/opt/browser-agent/main.py"
else
  echo "browser-agent main.py not found in /opt/browser-agent" >&2
  exit 1
fi

APP_NAME="$("${BROWSER_AGENT_PYTHON}" - <<'PY'
import pathlib
import tomllib

path = pathlib.Path("/opt/browser-agent/pyproject.toml")
if path.exists():
    print(tomllib.loads(path.read_text())["project"].get("name", "browser-agent"))
else:
    print("browser-agent")
PY
)"
APP_VERSION="$("${BROWSER_AGENT_PYTHON}" - <<'PY'
import pathlib
import tomllib

path = pathlib.Path("/opt/browser-agent/pyproject.toml")
if path.exists():
    print(tomllib.loads(path.read_text())["project"].get("version", "unknown"))
else:
    print("unknown")
PY
)"

export OTEL_RESOURCE_ATTRIBUTES="${OTEL_RESOURCE_ATTRIBUTES:-service.name=${APP_NAME}-${APP_ENV:-dev},service.version=${APP_VERSION},deployment.environment=${APP_ENV:-dev}}"

echo "Starting ${APP_NAME} ${APP_VERSION} on :8000..."
if [[ "${BROWSER_AGENT_ENABLE_OTEL:-true}" == "true" && -x "${BROWSER_AGENT_OTEL}" ]]; then
  "${BROWSER_AGENT_OTEL}" \
    --logs_exporter "${OTEL_LOGS_EXPORTER:-none}" \
    --traces_exporter "${OTEL_TRACES_EXPORTER:-none}" \
    --metrics_exporter "${OTEL_METRICS_EXPORTER:-none}" \
    "${BROWSER_AGENT_PYTHON}" "${BROWSER_AGENT_MAIN}" &
else
  "${BROWSER_AGENT_PYTHON}" "${BROWSER_AGENT_MAIN}" &
fi
BROWSER_AGENT_PID=$!

cd /opt/datafusion-agent
echo "Starting datafusion-agent on :${SERVER_PORT:-8081}..."
java ${JAVA_OPTS:-} \
  -Dspring.profiles.active="${SPRING_PROFILES_ACTIVE:-standalone,local}" \
  -jar /opt/datafusion-agent/datafusion-agent.jar &
DATAFUSION_AGENT_PID=$!

wait -n "${BROWSER_AGENT_PID}" "${DATAFUSION_AGENT_PID}"
EXIT_CODE=$?
term
exit "${EXIT_CODE}"
