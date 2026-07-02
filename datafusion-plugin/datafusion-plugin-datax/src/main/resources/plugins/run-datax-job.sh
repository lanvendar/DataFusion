#!/usr/bin/env bash

set -Eeuo pipefail

usage() {
  echo "Usage: $0 <datax-plugin-root-dir> <job-json-file-name> <log-root-dir>"
  echo "Example:"
  echo "  $0 /Users/lanvendar/Projects/DataFusion/datafusion-plugin/datafusion-plugin-datax/src/main/resources/plugins/datax shys/ods_shys_gb_account_td.json /tmp/datax-logs"
  echo "Environment:"
  echo "  DATAX_LOG_LEVEL=INFO|WARN|ERROR       default: INFO"
  echo "  DATAX_LOG_MAX_SIZE=<size>             default: 100MB, controlled by logback"
  echo "  DATAX_LOG_MAX_INDEX=<number>          default: 100, controlled by logback"
}

if [[ $# -ne 3 ]]; then
  usage
  exit 64
fi

datax_plugin_root="${1%/}"
job_name="$2"
log_root="${3%/}"

if [[ "$job_name" != *.json ]]; then
  echo "Error: job-json-file-name must end with .json: $job_name" >&2
  exit 64
fi

datax_home="${datax_plugin_root}"
job_root="${datax_plugin_root}/jobs"
datax_jar="${datax_home}/lib/datax-bundle-0.0.1.jar"
run_date="$(date +%Y%m%d)"
log_dir="${log_root}/${run_date}"
datax_log_level="${DATAX_LOG_LEVEL:-INFO}"
datax_log_max_size="${DATAX_LOG_MAX_SIZE:-100MB}"
datax_log_max_index="${DATAX_LOG_MAX_INDEX:-100}"

if [[ ! -d "$datax_plugin_root" ]]; then
  echo "Error: DataX plugin root dir does not exist: $datax_plugin_root" >&2
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
