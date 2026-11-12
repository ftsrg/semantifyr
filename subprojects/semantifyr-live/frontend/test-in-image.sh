#!/bin/sh

# SPDX-FileCopyrightText: 2026 The Semantifyr Authors
#
# SPDX-License-Identifier: EPL-2.0

set -eu

if [ $# -lt 2 ]; then
  echo "usage: $0 chromium|firefox|webkit screens|ui|flows [vitest arguments]" >&2
  exit 2
fi
engine=$1
level=$2
shift 2

frontend=$(cd "$(dirname "$0")" && pwd)
repository=$(git -C "$frontend" rev-parse --show-toplevel)
version=$(cd "$frontend" && node -p "require('playwright/package.json').version")

# the flows reach a backend on this machine's localhost, the other levels keep their servers inside the container
network=bridge
if [ "$level" = flows ]; then
  network=host
fi

exec docker run --rm --init --ipc=host --network "$network" \
  --user "$(id -u):$(id -g)" -e HOME=/tmp \
  -e TEST_BROWSER="$engine" -e SEMANTIFYR_LIVE_URL="${SEMANTIFYR_LIVE_URL:-}" \
  -v "$repository:$repository" -w "$frontend" \
  "mcr.microsoft.com/playwright:v$version-noble" \
  npm run "$level" -- "$@"
