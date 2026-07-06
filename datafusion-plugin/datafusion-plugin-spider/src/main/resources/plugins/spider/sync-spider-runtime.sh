#!/usr/bin/env bash
set -euo pipefail

# 把外部构建好的 SPIDER 运行时 tar.gz 同步到插件资源目录。
# 支持重复 --source 或逗号分隔 source 传参。
# source 未显式映射时按文件名自动映射：
#   browser-agent-linux-amd64-runtime.tar.gz -> browser-agent/
#   browser-agent-linux-amd64-venv.tar.gz -> browser-agent/
#   sh-web-spider-linux-amd64-runtime.tar.gz -> sh-web-spider/
#   sh-web-spider-linux-amd64-venv.tar.gz -> sh-web-spider/

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
MODULE_DIR="$(cd "${SCRIPT_DIR}/../../../../../" && pwd)"

DEFAULT_BROWSER_AGENT_SOURCE="${BROWSER_AGENT_RUNTIME_SOURCE:-/Users/lanvendar/PycharmProjects/browser-agent/dist/browser-agent-linux-amd64-runtime.tar.gz}"
DEFAULT_SH_WEB_SOURCE="${SH_WEB_SPIDER_RUNTIME_SOURCE:-/Users/lanvendar/PycharmProjects/sh-web-spider/dist/sh-web-spider-linux-amd64-runtime.tar.gz}"

TARGET_BASE="${SPIDER_RUNTIME_TARGET:-${MODULE_DIR}/src/main/resources/plugins/spider}"
SOURCES=()

usage() {
cat <<EOF
Usage: $0 [--source <path>] [--source <path> ...]

同步外部 SPIDER runtime tar.gz 到 datafusion-plugin-spider 资源目录。

默认目标:
  ${TARGET_BASE}

支持:
  --source <path1>,<path2>   # 支持逗号分隔多个
  --source <archive>         # 可重复调用
  --source <archive=targetDir> # 显式映射目录

示例:
  $0 --source /tmp/browser-agent-linux-amd64-runtime.tar.gz
  $0 --source /tmp/sh-web-spider-linux-amd64-runtime.tar.gz
  $0 --source /tmp/sh-web-spider-linux-amd64-runtime.tar.gz,/tmp/extra/runtime.tar.gz=tmp/runtime

默认映射文件名:
  browser-agent-linux-amd64-runtime.tar.gz -> browser-agent
  browser-agent-linux-amd64-venv.tar.gz -> browser-agent
  sh-web-spider-linux-amd64-runtime.tar.gz -> sh-web-spider
  sh-web-spider-linux-amd64-venv.tar.gz -> sh-web-spider

未传 --source 时，脚本会尝试使用默认路径:
  ${DEFAULT_BROWSER_AGENT_SOURCE}
  ${DEFAULT_SH_WEB_SOURCE}
EOF
}

while [ "$#" -gt 0 ]; do
  case "$1" in
    --source)
      if [ "$#" -lt 2 ]; then
        echo "Missing value for --source" >&2
        usage >&2
        exit 1
      fi
      IFS=',' read -r -a items <<< "${2:-}"
      for item in "${items[@]}"; do
        item="$(echo "${item}" | xargs)"
        [ -n "${item}" ] && SOURCES+=("${item}")
      done
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

if [ "${#SOURCES[@]}" -eq 0 ]; then
  if [ -n "${DEFAULT_BROWSER_AGENT_SOURCE}" ]; then
    SOURCES+=("${DEFAULT_BROWSER_AGENT_SOURCE}")
  fi
  if [ -n "${DEFAULT_SH_WEB_SOURCE}" ]; then
    SOURCES+=("${DEFAULT_SH_WEB_SOURCE}")
  fi
fi

if [ "${#SOURCES[@]}" -eq 0 ]; then
  echo "No runtime source provided." >&2
  usage >&2
  exit 1
fi

resolve_target_dir() {
  local source="$1"
  local name target
  if [[ "${source}" == *"="* ]]; then
    name="$(echo "${source}" | cut -d'=' -f1)"
    target="$(echo "${source}" | cut -d'=' -f2-)"
    if [ -z "${target}" ]; then
      echo "Invalid mapping, target dir is empty: ${source}" >&2
      exit 1
    fi
    echo "${name}" "${TARGET_BASE}/${target}"
    return
  fi
  if [[ "${source}" == *":"* ]]; then
    name="$(echo "${source}" | cut -d':' -f1)"
    target="$(echo "${source}" | cut -d':' -f2-)"
    if [ -z "${target}" ]; then
      echo "Invalid mapping, target dir is empty: ${source}" >&2
      exit 1
    fi
    echo "${name}" "${TARGET_BASE}/${target}"
    return
  fi

  local filename
  filename="$(basename "${source}")"
  case "${filename}" in
    browser-agent-linux-amd64-runtime.tar.gz)
      echo "${source}" "${TARGET_BASE}/browser-agent"
      ;;
    browser-agent-linux-amd64-venv.tar.gz)
      echo "${source}" "${TARGET_BASE}/browser-agent"
      ;;
    sh-web-spider-linux-amd64-runtime.tar.gz|sh-web-spider-linux-amd64-venv.tar.gz)
      echo "${source}" "${TARGET_BASE}/sh-web-spider"
      ;;
    *)
      echo "Unsupported runtime package: ${filename}" >&2
      exit 1
      ;;
  esac
}

sync_archive() {
  local entry source target_dir
  source="$1"
  if [ ! -f "${source}" ]; then
    echo "Runtime archive not found: ${source}" >&2
    exit 1
  fi

  if [[ "$(basename "${source}")" != *.tar.gz ]]; then
    echo "Unsupported archive suffix (expect .tar.gz): ${source}" >&2
    exit 1
  fi

  read -r source target_dir < <(resolve_target_dir "${source}")
  mkdir -p "${target_dir}"
  cp -f "${source}" "${target_dir}/"
  echo "Synced: ${source} -> ${target_dir}"
}

mkdir -p "${TARGET_BASE}"
mkdir -p "${TARGET_BASE}/browser-agent" "${TARGET_BASE}/sh-web-spider"

for entry in "${SOURCES[@]}"; do
  sync_archive "${entry}"
done

echo "Spider runtime sync done."
