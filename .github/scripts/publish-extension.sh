#!/bin/sh

# SPDX-FileCopyrightText: 2026 The Semantifyr Authors
#
# SPDX-License-Identifier: EPL-2.0

set -eu

extension=$1

if [ -z "${VSCE_PAT:-}" ]; then
    echo "VSCE_PAT is not set; the extension is not published to the marketplace"
    exit 0
fi
vsce=$(jq -r '.packages["node_modules/@vscode/vsce"].version' package-lock.json)
npx --yes "@vscode/vsce@$vsce" publish --packagePath "$extension" --skip-duplicate
