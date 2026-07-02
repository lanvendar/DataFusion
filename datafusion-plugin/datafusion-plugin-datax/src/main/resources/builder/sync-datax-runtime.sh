#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
MODULE_DIR="$(cd "${SCRIPT_DIR}/../../../.." && pwd)"

SOURCE_DIR="${DATAX_RUNTIME_SOURCE:-/Users/lanvendar/Projects/DataX/packaging/datax-bundle/target/datax}"
TARGET_DIR="${DATAX_RUNTIME_TARGET:-${MODULE_DIR}/src/main/resources/plugins/datax}"

usage() {
    cat <<EOF
Usage: $0 [--source <datax-runtime-dir>] [--target <datafusion-datax-plugin-dir>]

Sync external DataX runtime artifacts into datafusion-plugin-datax resources.

Defaults:
  source: ${SOURCE_DIR}
  target: ${TARGET_DIR}

The script syncs conf, lib, plugin and tmp. It does not touch jobs.
EOF
}

while [ "$#" -gt 0 ]; do
    case "$1" in
        --source)
            SOURCE_DIR="${2:-}"
            shift 2
            ;;
        --target)
            TARGET_DIR="${2:-}"
            shift 2
            ;;
        -h|--help)
            usage
            exit 0
            ;;
        *)
            echo "Unknown argument: $1" >&2
            usage >&2
            exit 1
            ;;
    esac
done

require_dir() {
    local dir="$1"
    if [ ! -d "${dir}" ]; then
        echo "Directory not found: ${dir}" >&2
        exit 1
    fi
}

require_file() {
    local file="$1"
    if [ ! -f "${file}" ]; then
        echo "File not found: ${file}" >&2
        exit 1
    fi
}

sync_dir() {
    local name="$1"
    require_dir "${SOURCE_DIR}/${name}"
    rm -rf "${TARGET_DIR}/${name}"
    mkdir -p "${TARGET_DIR}"
    cp -R "${SOURCE_DIR}/${name}" "${TARGET_DIR}/${name}"
}

require_dir "${SOURCE_DIR}"
require_dir "${SOURCE_DIR}/conf"
require_dir "${SOURCE_DIR}/lib"
require_dir "${SOURCE_DIR}/plugin"
require_dir "${SOURCE_DIR}/tmp"
require_file "${SOURCE_DIR}/lib/datax-bundle-0.0.1.jar"

sync_dir "conf"
sync_dir "lib"
sync_dir "plugin"
sync_dir "tmp"

echo "DataX runtime synced -> ${TARGET_DIR}"
