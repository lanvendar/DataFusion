#!/usr/bin/env bash
set -euo pipefail

# 扫描插件目录下的 .tar.gz 运行包，并解压到各自所在目录。

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
SOURCES=()

usage() {
  cat <<EOF
用法:
  ./init-runtime-unpack.sh [--root <plugins-dir>] [--source <runtime-tar1>[,<runtime-tar2>...]]

说明:
  未指定 --source 时，默认扫描 --root 目录下所有 *.tar.gz。
  每个运行包都会解压到它所在的目录。

示例:
  ./init-runtime-unpack.sh --root /opt/datafusion/plugins
  ./init-runtime-unpack.sh --source /opt/datafusion/plugins/spider/browser-agent/browser-agent-linux-amd64-runtime.tar.gz
EOF
}

while [ "$#" -gt 0 ]; do
  case "$1" in
    --root)
      [ $# -lt 2 ] && { echo "Missing value for --root" >&2; usage; exit 1; }
      ROOT_DIR="$2"
      shift 2
      ;;
    --source)
      [ $# -lt 2 ] && { echo "Missing value for --source" >&2; usage; exit 1; }
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

if [ ! -d "${ROOT_DIR}" ]; then
  echo "Plugin root directory not found: ${ROOT_DIR}" >&2
  exit 1
fi

if [ "${#SOURCES[@]}" -eq 0 ]; then
  while IFS= read -r archive; do
    SOURCES+=("${archive}")
  done < <(find "${ROOT_DIR}" -type f -name "*.tar.gz" | sort)
fi

if [ "${#SOURCES[@]}" -eq 0 ]; then
  echo "No runtime archives found under: ${ROOT_DIR}"
  exit 0
fi

for archive in "${SOURCES[@]}"; do
  if [[ "${archive}" != *.tar.gz ]]; then
    echo "Unsupported archive suffix, expect .tar.gz: ${archive}" >&2
    exit 1
  fi
  if [ ! -f "${archive}" ]; then
    echo "Archive not found: ${archive}" >&2
    exit 1
  fi

  target_dir="$(cd "$(dirname "${archive}")" && pwd)"
  echo "Unpack runtime archive: ${archive} -> ${target_dir}"
  tar -C "${target_dir}" -xzf "${archive}"
done

echo "Runtime unpack done."
