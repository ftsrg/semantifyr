#!/bin/sh

# SPDX-FileCopyrightText: 2026 The Semantifyr Authors
#
# SPDX-License-Identifier: EPL-2.0

set -eu

image=$1
version=$2
snapshot=$3

docker buildx imagetools create \
    --tag "$image:$version" \
    --tag "$image:latest" \
    "$image:$snapshot"
digest=$(docker buildx imagetools inspect "$image:$version" --format '{{.Manifest.Digest}}')
echo "digest=$digest" >> "$GITHUB_OUTPUT"
