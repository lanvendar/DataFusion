#!/usr/bin/env sh

set -eu

if [ "$#" -gt 0 ]; then
  case "$1" in
    bash|sh|java)
      exec "$@"
      ;;
    *.json)
      DATAX_JOB_FILE="$1"
      shift
      ;;
  esac
fi

DATAX_JAR="${DATAX_HOME}/lib/datax-bundle-0.0.1.jar"

if [ ! -d "$DATAX_HOME" ]; then
  echo "Error: DATAX_HOME does not exist: ${DATAX_HOME}" >&2
  exit 66
fi

if [ ! -f "$DATAX_JAR" ]; then
  echo "Error: DataX bundle jar does not exist: ${DATAX_JAR}" >&2
  exit 66
fi

if [ ! -f "$DATAX_JOB_FILE" ]; then
  echo "Error: DataX job file does not exist: ${DATAX_JOB_FILE}" >&2
  exit 66
fi

case "$DATAX_LOG_LEVEL" in
  TRACE|DEBUG|INFO|WARN|ERROR)
    ;;
  *)
  echo "Error: DATAX_LOG_LEVEL must be TRACE, DEBUG, INFO, WARN, or ERROR: ${DATAX_LOG_LEVEL}" >&2
  exit 64
    ;;
esac

case "$DATAX_LOG_MAX_INDEX" in
  ''|*[!0-9]*)
    echo "Error: DATAX_LOG_MAX_INDEX must be a positive integer: ${DATAX_LOG_MAX_INDEX}" >&2
    exit 64
    ;;
  *)
    if [ "$DATAX_LOG_MAX_INDEX" -lt 1 ]; then
      echo "Error: DATAX_LOG_MAX_INDEX must be a positive integer: ${DATAX_LOG_MAX_INDEX}" >&2
      exit 64
    fi
    ;;
esac

DATAX_LOG_DIR=${DATAX_LOG_FILE%/*}
if [ "$DATAX_LOG_DIR" != "$DATAX_LOG_FILE" ]; then
  mkdir -p "$DATAX_LOG_DIR"
fi

echo "============================================================"
echo "Start time      : $(date '+%Y-%m-%d %H:%M:%S')"
echo "DataX home      : ${DATAX_HOME}"
echo "Job file        : ${DATAX_JOB_FILE}"
echo "Log file        : ${DATAX_LOG_FILE}"
echo "DataX log level : ${DATAX_LOG_LEVEL}"
echo "Log max size    : ${DATAX_LOG_MAX_SIZE}"
echo "Log max index   : ${DATAX_LOG_MAX_INDEX}"
echo "============================================================"

# shellcheck disable=SC2086
exec java ${JAVA_OPTS} \
  --add-opens java.base/java.lang=ALL-UNNAMED \
  -Ddatax.home="${DATAX_HOME}" \
  -Ddatax.log.level="${DATAX_LOG_LEVEL}" \
  -Ddatax.log.file="${DATAX_LOG_FILE}" \
  -Ddatax.log.max.size="${DATAX_LOG_MAX_SIZE}" \
  -Ddatax.log.max.index="${DATAX_LOG_MAX_INDEX}" \
  -Dlogback.configurationFile="${DATAX_HOME}/conf/logback.xml" \
  -classpath "${DATAX_JAR}" \
  com.alibaba.datax.core.Engine \
  -mode standalone \
  -jobid "${DATAX_JOB_ID}" \
  -job "${DATAX_JOB_FILE}" \
  "$@"
