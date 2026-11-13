#!/bin/sh

# SPDX-FileCopyrightText: 2026 The Semantifyr Authors
#
# SPDX-License-Identifier: EPL-2.0

set -eu

extension=$1

# a release without both listings is refused before either is published
for token in VSCE_PAT OVSX_PAT; do
    if [ -z "$(printenv "$token" || true)" ]; then
        echo "$token is not set; the extension cannot be published to both marketplaces" >&2
        exit 1
    fi
done

vsce=$(jq -r '.packages["node_modules/@vscode/vsce"].version' package-lock.json)
npx --yes "@vscode/vsce@$vsce" publish --packagePath "$extension" --skip-duplicate
npx --yes ovsx@1.3.0 publish "$extension" --skip-duplicate
