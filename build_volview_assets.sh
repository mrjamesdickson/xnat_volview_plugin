#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
VOLVIEW_DIR="${ROOT_DIR}/frontend/VolView"
TARGET_DIR="${ROOT_DIR}/src/main/resources/META-INF/resources/volview/app"

if [[ ! -d "${VOLVIEW_DIR}" ]]; then
  echo "VolView submodule not found at ${VOLVIEW_DIR}. Did you initialise submodules?" >&2
  exit 1
fi

echo "==> Installing VolView dependencies"
cd "${VOLVIEW_DIR}"
npm ci

echo "==> Building VolView production bundle"
npm run build

BUILD_DIR="${VOLVIEW_DIR}/dist"
if [[ ! -d "${BUILD_DIR}" ]]; then
  echo "Build output not found at ${BUILD_DIR}" >&2
  exit 2
fi

echo "==> Copying VolView assets into plugin resources"
rm -rf "${TARGET_DIR}"
mkdir -p "${TARGET_DIR}"
cp -R "${BUILD_DIR}/"* "${TARGET_DIR}/"

echo "Done. Assets copied to ${TARGET_DIR}"
