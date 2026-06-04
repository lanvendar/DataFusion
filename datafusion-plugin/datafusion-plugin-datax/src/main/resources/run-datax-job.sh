#!/usr/bin/env bash

set -Eeuo pipefail

usage() {
  echo "Usage: $0 <resources-root-dir> <job-json-file-name> <log-root-dir>"
  echo "Example:"
  echo "  $0 /Users/lanvendar/Projects/DataFusion/datafusion-plugin/datafusion-plugin-datax/src/main/resources ods_shys_gb_account_td.json /tmp/datax-logs"
  echo "Environment:"
  echo "  DATAX_LOG_LEVEL=INFO|WARN|ERROR       default: INFO"
  echo "  DATAX_LOG_CHUNK_BYTES=<bytes>         default: 104857600, 0 means unlimited"
  echo "  DATAX_LOG_MAX_BYTES=<bytes>           deprecated alias for DATAX_LOG_CHUNK_BYTES"
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
log_file_base="${log_dir}/${job_name}.log"
datax_log_level="${DATAX_LOG_LEVEL:-INFO}"
datax_log_chunk_bytes="${DATAX_LOG_CHUNK_BYTES:-${DATAX_LOG_MAX_BYTES:-104857600}}"

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

if [[ ! "$datax_log_chunk_bytes" =~ ^[0-9]+$ ]]; then
  echo "Error: DATAX_LOG_CHUNK_BYTES must be a non-negative integer: $datax_log_chunk_bytes" >&2
  exit 64
fi

mkdir -p "$log_dir"
if [[ -f "$log_file_base" ]]; then
  rm -f "$log_file_base"
fi
shopt -s nullglob
old_log_files=("${log_file_base}".*)
if [[ ${#old_log_files[@]} -gt 0 ]]; then
  rm -f "${old_log_files[@]}"
fi
shopt -u nullglob
log_file="${log_file_base}.1"
: > "$log_file"

write_log_header() {
  {
    echo "============================================================"
    echo "Start time      : $(date '+%Y-%m-%d %H:%M:%S')"
    echo "DataX home      : ${datax_home}"
    echo "Job file        : ${job_file}"
    echo "Log file prefix : ${log_file_base}"
    echo "DataX log level : ${datax_log_level}"
    echo "Log chunk bytes : ${datax_log_chunk_bytes}"
    echo "============================================================"
  } | tee -a "$log_file"
}

latest_log_file() {
  local chunk=1
  local latest="${log_file_base}.${chunk}"

  while [[ -f "${log_file_base}.$((chunk + 1))" ]]; do
    chunk=$((chunk + 1))
    latest="${log_file_base}.${chunk}"
  done

  echo "$latest"
}

write_log_footer() {
  local status="$1"
  local footer_log_file
  footer_log_file="$(latest_log_file)"

  {
    echo "============================================================"
    echo "End time   : $(date '+%Y-%m-%d %H:%M:%S')"
    echo "Exit code  : ${status}"
    echo "============================================================"
  } | tee -a "$footer_log_file"
}

capture_rotated_log() {
  awk -v log_file_base="$log_file_base" -v chunk_bytes="$datax_log_chunk_bytes" '
    function log_file_name(chunk) {
      return log_file_base "." chunk
    }
    function rotate_if_needed(line_bytes) {
      if (!unlimited && written > 0 && written + line_bytes > chunk_bytes) {
        close(current_file)
        chunk += 1
        current_file = log_file_name(chunk)
        written = 0
      }
    }
    BEGIN {
      chunk_bytes += 0
      chunk = 1
      written = 0
      unlimited = chunk_bytes == 0
      current_file = log_file_name(chunk)
    }
    {
      line = $0 ORS
      line_bytes = length(line)
      rotate_if_needed(line_bytes)
      printf "%s", line
      printf "%s", line >> current_file
      fflush(current_file)
      written += line_bytes
    }
  '
}

write_log_header

set +e
java \
  --add-opens java.base/java.lang=ALL-UNNAMED \
  -Ddatax.home="${datax_home}" \
  -Ddatax.log.level="${datax_log_level}" \
  -Dlogback.configurationFile="${datax_home}/conf/logback.xml" \
  -classpath "${datax_jar}" \
  com.alibaba.datax.core.Engine \
  -mode standalone \
  -jobid -1 \
  -job "${job_file}" \
  2>&1 | capture_rotated_log
status=${PIPESTATUS[0]}
set -e

write_log_footer "$status"

exit "${status}"
