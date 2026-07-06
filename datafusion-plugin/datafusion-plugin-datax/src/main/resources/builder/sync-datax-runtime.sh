#!/usr/bin/env bash
set -euo pipefail

# 脚本用途：把外部构建好的 DataX runtime 同步到 datafusion-plugin-datax 的资源目录。
# 这是“同步脚本”，不负责任何编译（compile）动作。
# 设计约定：
# 1) 数据源来自外部目录（DATAX_RUNTIME_SOURCE），通常是 DataX 打包产物目录。
# 2) 同步目标是插件资源目录（MODULE_DIR 下的 src/main/resources/plugins/datax）。
# 3) 仅同步 runtime 必要目录，不处理 Job 配置文件，避免污染插件任务模板。

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
MODULE_DIR="$(cd "${SCRIPT_DIR}/../../../.." && pwd)"

# 数据源默认值：外部 DataX 打包产物目录；可通过 DATAX_RUNTIME_SOURCE 覆盖。
SOURCE_DIR="${DATAX_RUNTIME_SOURCE:-/Users/lanvendar/Projects/DataX/packaging/datax-bundle/target/datax}"
# 目标默认值：datafusion-plugin-datax 模块的插件资源目录；可通过 DATAX_RUNTIME_TARGET 覆盖。
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
