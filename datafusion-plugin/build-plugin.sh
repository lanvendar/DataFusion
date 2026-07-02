#!/usr/bin/env bash
set -euo pipefail

BUILD_MANIFEST_FILE=""

MODE="${PLUGIN_PACKAGE_MODE:-fat}"
SKIP_TESTS="${SKIP_TESTS:-true}"
RUN_MAVEN="${RUN_MAVEN:-true}"
MAVEN_CMD="${MAVEN_CMD:-mvn}"

usage() {
    cat <<EOF
Usage: $0 --manifest <plugin-build-manifest.json> [--mode fat|thin] [--skip-tests true|false] [--no-maven]

Build a Maven plugin module and publish its runtime files to the agent plugin resources.

Modes:
  fat   Copy executable shaded jar to the publish directory and keep lib empty.
  thin  Copy normal jar to the publish directory and runtime dependencies to lib.

For manifests with artifactMode=none, --mode is ignored and only runtime resources are published.
EOF
}

while [ "$#" -gt 0 ]; do
    case "$1" in
        --manifest)
            BUILD_MANIFEST_FILE="${2:-}"
            shift 2
            ;;
        --mode)
            MODE="${2:-}"
            shift 2
            ;;
        --skip-tests)
            SKIP_TESTS="${2:-}"
            shift 2
            ;;
        --no-maven)
            RUN_MAVEN="false"
            shift
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

case "${MODE}" in
    fat|thin)
        ;;
    *)
        echo "--mode must be fat or thin, got: ${MODE}" >&2
        exit 1
        ;;
esac

if [ -z "${BUILD_MANIFEST_FILE}" ]; then
    echo "--manifest is required." >&2
    usage >&2
    exit 1
fi

BUILD_MANIFEST_FILE="$(cd "$(dirname "${BUILD_MANIFEST_FILE}")" && pwd)/$(basename "${BUILD_MANIFEST_FILE}")"

read_manifest_value() {
    local key="$1"
    python3 - "${BUILD_MANIFEST_FILE}" "${key}" <<'PY'
import json
import sys

manifest_file, key = sys.argv[1], sys.argv[2]
with open(manifest_file, encoding="utf-8") as file:
    manifest = json.load(file)

value = manifest.get(key)
if isinstance(value, bool):
    print(str(value).lower())
elif value is None:
    print("")
else:
    print(value)
PY
}

read_manifest_array() {
    local key="$1"
    python3 - "${BUILD_MANIFEST_FILE}" "${key}" <<'PY'
import json
import sys

manifest_file, key = sys.argv[1], sys.argv[2]
with open(manifest_file, encoding="utf-8") as file:
    manifest = json.load(file)

value = manifest.get(key, [])
if not isinstance(value, list):
    raise SystemExit(f"{key} must be an array")
for item in value:
    print(item)
PY
}

read_manifest_file_mappings() {
    python3 - "${BUILD_MANIFEST_FILE}" <<'PY'
import json
import sys

with open(sys.argv[1], encoding="utf-8") as file:
    manifest = json.load(file)

for item in manifest.get("resourceFiles", []):
    if not isinstance(item, dict):
        raise SystemExit("resourceFiles items must be objects")
    source = item.get("source", "")
    target = item.get("target", "")
    if not source or not target:
        raise SystemExit("resourceFiles items must define source and target")
    print(f"{source}\t{target}")
PY
}

resolve_module_path() {
    local path="$1"
    case "${path}" in
        /*)
            echo "${path}"
            ;;
        *)
            echo "${MODULE_DIR}/${path}"
            ;;
    esac
}

resolve_repo_path() {
    local path="$1"
    case "${path}" in
        /*)
            echo "${path}"
            ;;
        *)
            echo "${REPO_ROOT}/${path}"
            ;;
    esac
}

load_build_manifest() {
    if [ ! -f "${BUILD_MANIFEST_FILE}" ]; then
        echo "Build manifest not found: ${BUILD_MANIFEST_FILE}" >&2
        exit 1
    fi
    if ! command -v python3 >/dev/null 2>&1; then
        echo "python3 is required to read build manifest: ${BUILD_MANIFEST_FILE}" >&2
        exit 1
    fi

    local artifact_mode manifest_dir plugin_type multi_app runtime_resource_dir agent_publish_dir
    manifest_dir="$(cd "$(dirname "${BUILD_MANIFEST_FILE}")" && pwd)"
    MODULE_DIR="$(cd "${manifest_dir}/../../../.." && pwd)"
    REPO_ROOT="$(cd "${MODULE_DIR}/../.." && pwd)"

    PLUGIN_TYPE="$(read_manifest_value "pluginType")"
    MODULE_PATH="$(read_manifest_value "modulePath")"
    ARTIFACT_ID="$(read_manifest_value "artifactId")"
    ARTIFACT_MODE="$(read_manifest_value "artifactMode")"
    multi_app="$(read_manifest_value "multiApp")"
    runtime_resource_dir="$(read_manifest_value "runtimeResourceDir")"
    agent_publish_dir="$(read_manifest_value "agentPublishDir")"
    artifact_mode="${ARTIFACT_MODE:-jar}"
    ARTIFACT_MODE="${artifact_mode}"

    plugin_type="${PLUGIN_TYPE}"
    if [ -z "${plugin_type}" ]; then
        echo "Build manifest must define pluginType." >&2
        exit 1
    fi
    if [ -z "${MODULE_PATH}" ]; then
        echo "Build manifest must define modulePath." >&2
        exit 1
    fi
    case "${ARTIFACT_MODE}" in
        jar|none)
            ;;
        *)
            echo "Build manifest artifactMode must be jar or none, got: ${ARTIFACT_MODE}" >&2
            exit 1
            ;;
    esac
    if [ "${ARTIFACT_MODE}" = "jar" ] && [ -z "${ARTIFACT_ID}" ]; then
        echo "Build manifest must define artifactId when artifactMode=jar." >&2
        exit 1
    fi
    if [ "${multi_app}" != "false" ]; then
        echo "Generic plugin builder currently supports multiApp=false, got: ${multi_app}" >&2
        exit 1
    fi
    if [ -z "${runtime_resource_dir}" ] || [ -z "${agent_publish_dir}" ]; then
        echo "Build manifest must define runtimeResourceDir and agentPublishDir." >&2
        exit 1
    fi

    TARGET_DIR="${MODULE_DIR}/target"
    SOURCE_RUNTIME_DIR="$(resolve_module_path "${runtime_resource_dir}")"
    AGENT_PUBLISH_DIR="$(resolve_repo_path "${agent_publish_dir}")"
}

run_maven_package() {
    local mvn_args=("-q" "-pl" "${MODULE_PATH}" "-am")
    if [ "${RUN_MAVEN}" != "true" ]; then
        return
    fi
    if [ "${SKIP_TESTS}" = "true" ]; then
        mvn_args+=("-DskipTests")
    fi
    (cd "${REPO_ROOT}" && "${MAVEN_CMD}" "${mvn_args[@]}" package)
}

copy_runtime_dependencies() {
    local output_dir="$1"
    if [ "${RUN_MAVEN}" = "true" ]; then
        rm -rf "${output_dir}"
        mkdir -p "${output_dir}"
        (cd "${REPO_ROOT}" && "${MAVEN_CMD}" -q -pl "${MODULE_PATH}" -am dependency:copy-dependencies \
            -DincludeScope=runtime \
            -DoutputDirectory="${output_dir}")
        return
    fi
    if [ ! -d "${output_dir}" ]; then
        echo "Runtime dependency directory not found: ${output_dir}" >&2
        echo "Run without --no-maven or prepare target/plugin-lib first." >&2
        exit 1
    fi
}

find_fat_jar() {
    find "${TARGET_DIR}" -maxdepth 1 -type f -name "${ARTIFACT_ID}-*-executable.jar" | sort | tail -n 1
}

find_thin_jar() {
    find "${TARGET_DIR}" -maxdepth 1 -type f -name "${ARTIFACT_ID}-*.jar" \
        ! -name "*-executable.jar" \
        ! -name "*-sources.jar" \
        ! -name "*-javadoc.jar" | sort | tail -n 1
}

require_file() {
    local file="$1"
    local message="$2"
    if [ -z "${file}" ] || [ ! -f "${file}" ]; then
        echo "${message}" >&2
        exit 1
    fi
}

copy_resource_dir() {
    local name="$1"
    local source="${SOURCE_RUNTIME_DIR}/${name}"
    local target="${AGENT_PUBLISH_DIR}/${name}"
    if [ ! -d "${source}" ]; then
        echo "Configured resource directory not found: ${source}" >&2
        exit 1
    fi
    rm -rf "${target}"
    mkdir -p "${AGENT_PUBLISH_DIR}"
    cp -R "${source}" "${target}"
}

copy_resource_file() {
    local source="$1"
    local target="$2"
    if [ ! -f "${source}" ]; then
        echo "Configured resource file not found: ${source}" >&2
        exit 1
    fi
    mkdir -p "$(dirname "${target}")"
    cp "${source}" "${target}"
}

publish_common_resources() {
    local dir source target
    mkdir -p "${AGENT_PUBLISH_DIR}"
    while IFS= read -r dir; do
        [ -n "${dir}" ] || continue
        copy_resource_dir "${dir}"
    done < <(read_manifest_array "resourceDirs")

    while IFS=$'\t' read -r source target; do
        [ -n "${source}" ] || continue
        copy_resource_file "$(resolve_module_path "${source}")" "${AGENT_PUBLISH_DIR}/${target}"
    done < <(read_manifest_file_mappings)
}

publish_fat() {
    local jar_file
    jar_file="$(find_fat_jar)"
    require_file "${jar_file}" "Executable jar not found. Run Maven package first."
    rm -f "${AGENT_PUBLISH_DIR}/${ARTIFACT_ID}-"*.jar
    rm -rf "${AGENT_PUBLISH_DIR}/lib"
    mkdir -p "${AGENT_PUBLISH_DIR}/lib"
    cp "${jar_file}" "${AGENT_PUBLISH_DIR}/"
}

publish_thin() {
    local jar_file deps_dir
    jar_file="$(find_thin_jar)"
    require_file "${jar_file}" "Thin jar not found. Run Maven package first."
    deps_dir="${TARGET_DIR}/plugin-lib"
    copy_runtime_dependencies "${deps_dir}"
    rm -f "${AGENT_PUBLISH_DIR}/${ARTIFACT_ID}-"*.jar
    rm -rf "${AGENT_PUBLISH_DIR}/lib"
    mkdir -p "${AGENT_PUBLISH_DIR}/lib"
    cp "${jar_file}" "${AGENT_PUBLISH_DIR}/"
    if [ -d "${deps_dir}" ]; then
        find "${deps_dir}" -maxdepth 1 -type f -name "*.jar" -exec cp {} "${AGENT_PUBLISH_DIR}/lib/" \;
    fi
}

load_build_manifest
run_maven_package
publish_common_resources

case "${MODE}" in
    fat)
        if [ "${ARTIFACT_MODE}" = "jar" ]; then
            publish_fat
        fi
        ;;
    thin)
        if [ "${ARTIFACT_MODE}" = "jar" ]; then
            publish_thin
        fi
        ;;
esac

echo "${PLUGIN_TYPE} plugin published (${MODE}, artifactMode=${ARTIFACT_MODE}) -> ${AGENT_PUBLISH_DIR}"
