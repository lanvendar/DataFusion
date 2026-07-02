#!/usr/bin/env bash

set -Eeuo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
MODULE_DIR="$(cd "${SCRIPT_DIR}/../../.." && pwd)"
DEFAULT_DATAX_HOME="${SCRIPT_DIR}/plugins/datax"
DEFAULT_LOG_ROOT="${MODULE_DIR}/target/datax-logs"

usage() {
  echo "Usage: $0 <job-json-file-name-or-path> [log-root-dir] [datax-home]"
  echo "Example:"
  echo "  $0 shys/ods_shys_gb_account_td.json"
  echo "  $0 ods_shys_gb_account_td.json /tmp/datax-logs"
  echo "Environment:"
  echo "  DATAX_LOG_LEVEL=INFO|WARN|ERROR       default: INFO"
  echo "  DATAX_LOG_MAX_SIZE=<size>             default: 100MB, controlled by logback"
  echo "  DATAX_LOG_MAX_INDEX=<number>          default: 100, controlled by logback"
  echo "Defaults:"
  echo "  datax-home: ${DEFAULT_DATAX_HOME}"
  echo "  log-root  : ${DEFAULT_LOG_ROOT}"
}

if [[ $# -eq 1 && ( "$1" == "-h" || "$1" == "--help" ) ]]; then
  usage
  exit 0
fi

if [[ $# -lt 1 || $# -gt 3 ]]; then
  usage
  exit 64
fi

job_name="$1"
log_root="${2:-${DEFAULT_LOG_ROOT}}"
datax_home="${3:-${DEFAULT_DATAX_HOME}}"
log_root="${log_root%/}"
datax_home="${datax_home%/}"

if [[ "$job_name" != *.json ]]; then
  echo "Error: job-json-file-name must end with .json: $job_name" >&2
  exit 64
fi

job_root="${datax_home}/jobs"
datax_jar="${datax_home}/lib/datax-bundle-0.0.1.jar"
run_date="$(date +%Y%m%d)"
log_dir="${log_root}/${run_date}"
datax_log_level="${DATAX_LOG_LEVEL:-INFO}"
datax_log_max_size="${DATAX_LOG_MAX_SIZE:-100MB}"
datax_log_max_index="${DATAX_LOG_MAX_INDEX:-100}"

if [[ ! -d "$datax_home" ]]; then
  echo "Error: datax home does not exist: $datax_home" >&2
  exit 66
fi

if [[ ! -f "$datax_jar" ]]; then
  echo "Error: datax bundle jar does not exist: $datax_jar" >&2
  exit 66
fi

if [[ "$job_name" == /* ]]; then
  job_file="$job_name"
elif [[ "$job_name" == */* ]]; then
  job_file="${job_root}/${job_name}"
else
  direct_job_file="${job_root}/${job_name}"
  if [[ -f "$direct_job_file" ]]; then
    job_file="$direct_job_file"
  else
    job_matches=()
    while IFS= read -r matched_job_file; do
      job_matches+=("$matched_job_file")
    done < <(find "$job_root" -type f -name "$job_name" | sort)

    if [[ ${#job_matches[@]} -eq 1 ]]; then
      job_file="${job_matches[0]}"
    elif [[ ${#job_matches[@]} -eq 0 ]]; then
      echo "Error: job file does not exist under ${job_root}: ${job_name}" >&2
      exit 66
    else
      echo "Error: job file name is ambiguous under ${job_root}: ${job_name}" >&2
      printf '  %s\n' "${job_matches[@]}" >&2
      exit 66
    fi
  fi
fi

if [[ ! -f "$job_file" ]]; then
  echo "Error: job file does not exist: $job_file" >&2
  exit 66
fi

job_file_name="$(basename "$job_file")"
log_file="${log_dir}/${job_file_name}.log"

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
