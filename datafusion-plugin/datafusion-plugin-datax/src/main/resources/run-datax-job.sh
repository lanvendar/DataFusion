#!/usr/bin/env bash

set -Eeuo pipefail

usage() {
  echo "Usage: $0 <resources-root-dir> <job-json-file-name> <log-root-dir>"
  echo "Example:"
  echo "  $0 /Users/lanvendar/Projects/DataFusion/datafusion-plugin/datafusion-plugin-datax/src/main/resources ods_shys_gb_account_td.json /tmp/datax-logs"
  echo "Environment:"
  echo "  DATAX_LOG_LEVEL=INFO|WARN|ERROR       default: INFO"
  echo "  DATAX_LOG_MAX_SIZE=<size>             default: 100MB, controlled by logback"
  echo "  DATAX_LOG_MAX_INDEX=<number>          default: 100, controlled by logback"
}

if [[ $# -ne 3 ]]; then
  usage
  exit 64
fi

resources_root="${1%/}"
job_name="$2"
log_root="${3%/}"

if [[ "$job_name" == */* ]]; then
  echo "Error: job-json-file-name must be a file name, not a path: $job_name" >&2
  exit 64
fi

if [[ "$job_name" != *.json ]]; then
  echo "Error: job-json-file-name must end with .json: $job_name" >&2
  exit 64
fi

datax_home="${resources_root}/datax"
job_file="${resources_root}/job/${job_name}"
datax_jar="${datax_home}/lib/datax-bundle-0.0.1.jar"
run_date="$(date +%Y%m%d)"
log_dir="${log_root}/${run_date}"
log_file="${log_dir}/${job_name}.log"
datax_log_level="${DATAX_LOG_LEVEL:-INFO}"
datax_log_max_size="${DATAX_LOG_MAX_SIZE:-100MB}"
datax_log_max_index="${DATAX_LOG_MAX_INDEX:-100}"

if [[ ! -d "$resources_root" ]]; then
  echo "Error: resources root dir does not exist: $resources_root" >&2
  exit 66
fi

if [[ ! -d "$datax_home" ]]; then
  echo "Error: datax home does not exist: $datax_home" >&2
  exit 66
fi

if [[ ! -f "$datax_jar" ]]; then
  echo "Error: datax bundle jar does not exist: $datax_jar" >&2
  exit 66
fi

if [[ ! -f "$job_file" ]]; then
  echo "Error: job file does not exist: $job_file" >&2
  exit 66
fi

if [[ ! "$datax_log_level" =~ ^(INFO|WARN|ERROR)$ ]]; then
  echo "Error: DATAX_LOG_LEVEL must be INFO, WARN, or ERROR: $datax_log_level" >&2
  exit 64
fi

if [[ ! "$datax_log_max_index" =~ ^[0-9]+$ ]] || [[ "$datax_log_max_index" -lt 1 ]]; then
  echo "Error: DATAX_LOG_MAX_INDEX must be a positive integer: $datax_log_max_index" >&2
  exit 64
fi

mkdir -p "$log_dir"

write_log_header() {
  echo "============================================================"
  echo "Start time      : $(date '+%Y-%m-%d %H:%M:%S')"
  echo "DataX home      : ${datax_home}"
  echo "Job file        : ${job_file}"
  echo "Log file        : ${log_file}"
  echo "DataX log level : ${datax_log_level}"
  echo "Log max size    : ${datax_log_max_size}"
  echo "Log max index   : ${datax_log_max_index}"
  echo "============================================================"
}

write_log_footer() {
  local status="$1"
  echo "============================================================"
  echo "End time   : $(date '+%Y-%m-%d %H:%M:%S')"
  echo "Exit code  : ${status}"
  echo "============================================================"
}

write_log_header

set +e
java \
  --add-opens java.base/java.lang=ALL-UNNAMED \
  -Ddatax.home="${datax_home}" \
  -Ddatax.log.level="${datax_log_level}" \
  -Ddatax.log.file="${log_file}" \
  -Ddatax.log.max.size="${datax_log_max_size}" \
  -Ddatax.log.max.index="${datax_log_max_index}" \
  -Dlogback.configurationFile="${datax_home}/conf/logback.xml" \
  -classpath "${datax_jar}" \
  com.alibaba.datax.core.Engine \
  -mode standalone \
  -jobid -1 \
  -job "${job_file}"
status=$?
set -e

write_log_footer "$status"

exit "${status}"
